"""Verify the candidate manifest and run the deterministic integration baseline."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import stat
from xml.etree import ElementTree
from zipfile import ZipFile

REPO = Path(__file__).resolve().parents[2]
MANIFEST = Path(__file__).with_name("manifest.v1.1.json")
NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
STAGES = {"BUILD", "TESTING", "QUALITY_GATE", "DEPLOYMENT", "CONTAINER", "INFRASTRUCTURE"}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(value, message):
    if not value:
        raise ValueError(message)


def git(repo, *args):
    return subprocess.check_output(["git", "-C", repo, *args])


def verify_workbook(manifest, workbook):
    source = manifest["source"]
    require(sha(workbook.read_bytes()) == source["workbook_sha256"], "Workbook checksum differs")
    with ZipFile(workbook) as archive:
        shared = ElementTree.fromstring(archive.read("xl/sharedStrings.xml"))
        strings = ["".join(t.text or "" for t in node.findall(".//x:t", NS)) for node in shared]
        sheet = ElementTree.fromstring(archive.read(source["worksheet"]))
    rows = {int(node.attrib["r"]): node for node in sheet.findall(".//x:row", NS)}
    for case in manifest["candidates"]:
        cells = {}
        for cell in rows[case["source_row"]]:
            value = cell.findtext("x:v", namespaces=NS)
            if value is not None:
                column = cell.attrib["r"].rstrip("0123456789")
                cells[column] = strings[int(value)] if cell.get("t") == "s" else value
        values = [cells[name] for name in source["row_columns"]]
        canonical = json.dumps(values, ensure_ascii=False, separators=(",", ":")).encode()
        require(sha(canonical) == case["row_sha256"], f"Source row checksum differs for {case['id']}")
        require((cells["A"], cells["B"], cells["H"]) ==
                (case["dataset_job_id"], case["job_url"], case["workbook_label_tag"]),
                f"Source metadata differs for {case['id']}")
    print(f"Verified workbook and {len(manifest['candidates'])} pinned rows")


def run_baseline(manifest, args):
    baseline = manifest["baseline"]
    case = next((entry for entry in [baseline, *manifest["synthetic_scenarios"]]
                 if entry["id"] == args.scenario), None)
    require(case is not None, "External candidates are planned and are not executable")
    managed = args.foundry_ledger is not None
    require(not managed or case["id"] in {"E02", "E03"}, "Foundry increment admits only E02 and E03")
    if managed:
        require(sha(MANIFEST.read_bytes()) == "14f23d2c59c726bcf29867ec1eb2ecbb11145c7a363a88d76f3810a493a390eb",
                "Managed scenarios require the immutable v1.1.0 manifest")
    refs = dict(baseline["refs"])
    if args.review_recommendation_ref:
        require(case["id"] == "E02" and not managed and args.browser,
                "Integrated review requires deterministic E02 and the review browser harness")
        refs["recommendation"] = git(REPO.parent / "pipesor-recommendation-service", "rev-parse",
                                    "--verify", args.review_recommendation_ref + "^{commit}").decode().strip()
    checkout = REPO / case.get("log", "tools/local-handoff/checkout-failure.log")
    require(sha(checkout.read_bytes()) == case["checkout_log_sha256"], "Log fixture checksum differs")
    envelope = ((REPO / case["envelope"]).read_bytes() if case is not baseline else
                git(REPO.parent / "pipesor-log-event-service", "show",
                    refs["log_event"] + ":src/test/resources/protected-workflow-evidence-v2.json"))
    require(sha(envelope) == case["envelope_sha256"], "Envelope checksum differs")
    env = {k: os.environ[k] for k in ("PATH", "HOME", "JAVA_HOME", "DOCKER_HOST", "DOCKER_CONTEXT",
                                     "DOCKER_CONFIG", "PYTHON_BIN", "PIPESOR_PLAYWRIGHT_MODULE", "PIPESOR_CHROMIUM_EXECUTABLE") if k in os.environ}
    env.update(PIPESOR_DEMO_RAG_MODE="fixture" if case is baseline else "pgvector-fixture",
               PIPESOR_DEMO_LLM_MODE="fixture", PIPESOR_DEMO_SCENARIO=case["id"])
    env.update({f"PIPESOR_DEMO_{k.upper()}_REF": v for k, v in refs.items()})
    if managed:
        ledger = args.foundry_ledger.resolve(strict=True)
        require(ledger.is_dir() and ledger.stat().st_uid == os.getuid()
                and stat.S_IMODE(ledger.stat().st_mode) == 0o700 and not ledger.is_relative_to(REPO),
                "Use one private mode-700 ledger outside Git for the entire managed increment")
        require(not list(ledger.glob("attempt-*.json")) or not (ledger / f"{case['id']}.started").exists(),
                "The scenario has already started in this ledger")
        env.update(PIPESOR_DEMO_RAG_MODE="hybrid", PIPESOR_DEMO_LLM_MODE="foundry",
                   PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR=str(ledger),
                   PIPESOR_RAG_AZURE_OPENAI_ENDPOINT="https://ai-pipesor-dev-04f339b7.services.ai.azure.com",
                   PIPESOR_RAG_AZURE_OPENAI_DEPLOYMENT="pipesor-dev-embeddings",
                   PIPESOR_RAG_EMBEDDING_MODEL_VERSION="1")
        if "AZURE_CONFIG_DIR" in os.environ:
            env["AZURE_CONFIG_DIR"] = os.environ["AZURE_CONFIG_DIR"]
    if args.review_recommendation_ref:
        env["PIPESOR_DEMO_REVIEW"] = "1"
    if args.browser:
        env["PIPESOR_DEMO_BROWSER_CHECK"] = str(args.browser.resolve(strict=True))
    directory = args.evidence.resolve()
    require(not directory.is_relative_to(REPO), "Use a fresh evidence directory outside the repository")
    directory.mkdir(mode=0o700)
    if managed:
        (ledger / f"{case['id']}.started").touch(exist_ok=False)
    log_path = directory / "owner-chain.log"
    with log_path.open("xb") as stream:
        result = subprocess.run(["bash", "tools/verify_local_owner_chain.sh"], cwd=REPO, env=env,
                                stdout=stream, stderr=subprocess.STDOUT)
    raw = log_path.read_bytes()
    output = raw.decode(errors="replace")
    markers = ("PIPESOR_DIAGNOSIS ", "PIPESOR_DIAGNOSTIC_HTTP 401 503 200",
               "PIPESOR_PRELIMINARY_NEGATIVES", "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0", "BUILD SUCCESS")
    if case is not baseline:
        statuses = "authorized=matches other=no_match" if case["id"] == "E02" else "authorized=no_match other=matches"
        vector_mode = "azure_openai 1" if managed else "deterministic simulated-v1"
        markers += (f"PIPESOR_RAG_IMPORT postgres pgvector 1536 {vector_mode}",
                    f"PIPESOR_RAG_HTTP {statuses} wrong-caller=403")
    if managed:
        markers += ("PIPESOR_FOUNDRY_VALIDATION accepted evidence and citation contract", "PIPESOR_EMBEDDING_USAGE ")
    if args.review_recommendation_ref:
        markers += ("PIPESOR_DIAGNOSIS_ADMITTED ", "PIPESOR_INTEGRATED_REVIEW_RECEIPT ",
                    "PIPESOR_INTEGRATED_REVIEW persisted CONFIRMED CORRECTED REJECTED; cross-scope 404",
                    "P3_REACT CONFIRMED", "P3_REACT CORRECTED", "P3_REACT REJECTED")
    elif args.browser:
        markers += ("PIPESOR_DIAGNOSTIC_BROWSER en", "PIPESOR_DIAGNOSTIC_BROWSER es")
    missing = [m for m in markers if m not in output]
    passed = result.returncode == 0 and not missing
    receipt = {
        "manifest_version": manifest["version"], "manifest_sha256": sha(MANIFEST.read_bytes()),
        "scenario": case["id"], "status": "passed" if passed else "failed",
        "mode": "real Azure embeddings and GPT-4o" if managed else "deterministic; no managed embeddings or chat",
        "rag_storage": "fixture" if case is baseline else "PostgreSQL 17/pgvector with Alembic",
        "reference_cause": case.get("reference_cause"),
        "rag_scope_probe": next((line for line in output.splitlines() if line.startswith("PIPESOR_RAG_HTTP ")), None),
        "diagnosis": next((json.loads(line.removeprefix("PIPESOR_DIAGNOSIS ")) for line in output.splitlines()
                           if line.startswith("PIPESOR_DIAGNOSIS ")), None),
        "exit_code": result.returncode, "missing_markers": missing, "peer_revisions": refs,
        "correlation_revision": git(REPO, "rev-parse", "HEAD").decode().strip(),
        "correlation_worktree_diff_sha256": sha(git(REPO, "diff", "HEAD")),
        "runner_sha256": sha(Path(__file__).read_bytes()),
        "checkout_log_sha256": case["checkout_log_sha256"], "envelope_sha256": case["envelope_sha256"],
        "owner_chain_log_sha256": sha(raw), "browser_requested": bool(args.browser),
        "properties": {
            "P1": "partially verified for one instrumented local execution" if passed else "not verified by this run",
            "P2": ("partially verified; authorized retrieval and no-match; see separate scope evidence"
                   if passed else "not verified by this run"),
            "P3": "pending; no human-review transition exercised",
        },
    }
    if args.review_recommendation_ref:
        marker = "PIPESOR_INTEGRATED_REVIEW_RECEIPT "
        receipt["human_review"] = next((json.loads(line.removeprefix(marker)) for line in output.splitlines()
                                        if line.startswith(marker)), None)
        receipt["properties"]["P3"] = ("locally exercised through three persisted browser submissions"
                                      if passed else "not verified by this run")
    files = ["owner-chain.log", "receipt.json"]
    if managed:
        def marked(marker):
            return [json.loads(line.removeprefix(marker)) for line in output.splitlines() if line.startswith(marker)]

        receipt.update(evidence_version="1.2.0", embedding_model_version="1",
                       embedding_usage=marked("PIPESOR_EMBEDDING_USAGE "),
                       model_input_chunk_ids=marked("PIPESOR_MODEL_INPUT "),
                       citation_validated_outputs=marked("PIPESOR_VALIDATED_OUTPUT "))
        for path in sorted(ledger.glob("attempt-*.json")):
            if not path.stat().st_size or json.loads(path.read_text()).get("scenario") != case["id"]:
                continue
            (directory / path.name).write_bytes(path.read_bytes())
            files.append(path.name)
    (directory / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    (directory / "SHA256SUMS").write_text("".join(
        f"{sha((directory / name).read_bytes())}  {name}\n" for name in files))
    print(f"{receipt['status']}: {directory / 'receipt.json'}")
    return int(not passed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    verify = commands.add_parser("verify")
    verify.add_argument("--workbook", type=Path)
    run = commands.add_parser("run")
    run.add_argument("scenario")
    run.add_argument("--evidence", type=Path, required=True)
    run.add_argument("--browser", type=Path)
    run.add_argument("--review-recommendation-ref")
    run.add_argument("--foundry-ledger", type=Path)
    args = parser.parse_args()
    try:
        manifest = json.loads(MANIFEST.read_text())
        cases = manifest["candidates"]
        require(len(cases) == len({c["id"] for c in cases}) == len({c["source_row"] for c in cases}) == 10,
                "Expected ten unique source-backed candidates")
        require(all(c["status"] == "planned" and c["raw_log_sha256"] is None and
                    c["normalization_stage"] in STAGES and re.fullmatch(r"[a-f0-9]{64}", c["row_sha256"])
                    for c in cases), "Unexpected external candidate state")
        if args.command == "verify":
            if args.workbook:
                verify_workbook(manifest, args.workbook)
            else:
                print("Manifest structure and ten planned candidates verified")
            return 0
        return run_baseline(manifest, args)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(2, f"{error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
