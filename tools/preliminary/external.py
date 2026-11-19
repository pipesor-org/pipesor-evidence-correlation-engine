"""Replay reviewed LogChunks excerpts locally via a synthetic contract envelope."""

import argparse
import csv
import io
import json
import os
import subprocess
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5
from zipfile import ZipFile

from run import REPO, git, require, sha

HERE = Path(__file__).parent


def prepare(archive, case, dataset, refs):
    raw = archive.read(case["log_member"])
    annotation = archive.read(case["annotation_member"])
    require(sha(raw) == case["raw_sha256"], "Original log hash mismatch")
    require(sha(annotation) == case["annotation_sha256"], "Annotation hash mismatch")
    validation = archive.read(dataset["crossvalidation_member"])
    require(sha(validation) == dataset["crossvalidation_sha256"], "Cross-validation hash mismatch")
    rows = csv.DictReader(io.StringIO(validation.decode("utf-8-sig")))
    require(any(row["build_id"] == case["build_id"] and row["extraction valid"] == "true"
                for row in rows), "Missing developer confirmation of extraction")
    line = raw.split(b"\n")[case["line"] - 1].decode("utf-8")
    require(sha(line.encode()) == case["line_sha256"], "Original line hash mismatch")
    # Use the reviewed projection only after confirming that the source bytes match.
    log = ("2026-11-08T09:20:31Z ##[error]" + case["sanitized_excerpt"] + "\n").encode()
    envelope = json.loads(git(REPO.parent / "pipesor-log-event-service", "show",
                              refs["log_event"] + ":src/test/resources/protected-workflow-evidence-v2.json"))
    envelope["message_id"] = str(uuid5(NAMESPACE_URL, "urn:pipesor:logchunks:" + case["id"]))
    envelope["correlation_id"] = "external-logchunks-" + case["id"].lower()
    envelope["execution"]["github_run_id"] = "9999999999999999999999000" + case["id"][-1]
    envelope["evidence"]["stage"] = case["stage"]
    return log, (json.dumps(envelope, indent=2) + "\n").encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("scenario")
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--managed-bundle", type=Path)
    args = parser.parse_args()
    manifest_path = HERE / "external-manifest.v1.json"
    manifest = json.loads(manifest_path.read_text())
    case = next(entry for entry in manifest["cases"] if entry["id"] == args.scenario)
    require(sha(args.archive.read_bytes()) == manifest["dataset"]["archive_sha256"], "Archive hash mismatch")
    with ZipFile(args.archive) as archive:
        log, envelope = prepare(archive, case, manifest["dataset"], manifest["peer_revisions"])
    managed = args.managed_bundle is not None
    if managed:
        from verify_external_foundry_inputs import BUNDLE_SHA, CASES

        require(case["id"] in CASES and (case["build_id"], case["sanitized_excerpt"]) == CASES[case["id"]],
                "Input is outside the reviewed E05/E06 allowlist")
        require(sha(args.managed_bundle.read_bytes()) == BUNDLE_SHA, "Reviewed bundle hash differs")
        with ZipFile(args.managed_bundle) as bundle:
            require(bundle.read("manifest.json") == manifest_path.read_bytes(), "Reviewed manifest differs")
            require((log, envelope) == (bundle.read(f"{case['id']}/log.txt"),
                                        bundle.read(f"{case['id']}/envelope.json")), "Reviewed inputs differ")
        # Fixed reservations in nanodollars, shared across the two nonreusable case directories.
        reserved = 2 * (8192 * 2750 + 1024 * 11000 + 8 * 512 * 22)
        require(reserved <= 70_000_000, "Approved budget insufficient")
    out = args.evidence.resolve()
    require(not out.is_relative_to(REPO), "Use a new evidence directory outside the repository")
    if managed:
        require(out.name == case["id"] and out.parent.stat().st_mode & 0o777 == 0o700,
                "Use E05 and E06 directories in the same protected campaign directory")
    out.mkdir(mode=0o700)
    (out / "log.txt").write_bytes(log)
    (out / "envelope.json").write_bytes(envelope)
    env = {k: os.environ[k] for k in ("PATH", "HOME", "JAVA_HOME", "DOCKER_HOST", "DOCKER_CONTEXT",
                                      "DOCKER_CONFIG", "PYTHON_BIN") if k in os.environ}
    env.update(PIPESOR_DEMO_SCENARIO="external", PIPESOR_DEMO_LLM_MODE="fixture",
               PIPESOR_DEMO_RAG_MODE="pgvector-fixture", PIPESOR_DEMO_EXTERNAL_INPUT=str(out))
    if managed:
        env.update(PIPESOR_DEMO_LLM_MODE="foundry", PIPESOR_DEMO_RAG_MODE="hybrid",
                   PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR=str(out), PIPESOR_DEMO_EXTERNAL_CASE=case["id"],
                   PIPESOR_RAG_AZURE_OPENAI_ENDPOINT="https://ai-pipesor-dev-04f339b7.services.ai.azure.com",
                   PIPESOR_RAG_AZURE_OPENAI_DEPLOYMENT="pipesor-dev-embeddings",
                   PIPESOR_RAG_EMBEDDING_MODEL_VERSION="1")
    env.update({f"PIPESOR_DEMO_{k.upper()}_REF": v for k, v in manifest["peer_revisions"].items()})
    with (out / "owner-chain.log").open("xb") as output:
        proc = subprocess.run(["bash", "tools/verify_local_owner_chain.sh"], cwd=REPO, env=env,
                              stdout=output, stderr=subprocess.STDOUT)
    lines = (out / "owner-chain.log").read_text(errors="replace").splitlines()
    diagnosis = next((json.loads(line.split(" ", 1)[1]) for line in lines
                      if line.startswith("PIPESOR_DIAGNOSIS ")), None)
    markers = ("PIPESOR_RAG_HTTP authorized=matches other=no_match wrong-caller=403",
               "PIPESOR_DIAGNOSTIC_HTTP 401 503 200", "PIPESOR_PRELIMINARY_NEGATIVES",
               "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0", "BUILD SUCCESS")
    if managed:
        markers += ("PIPESOR_FOUNDRY_VALIDATION accepted evidence and citation contract",
                    "PIPESOR_RAG_IMPORT postgres pgvector 1536 azure_openai 1")
    missing = [marker for marker in markers if not any(marker in line for line in lines)]
    preserved = bool(diagnosis and diagnosis["evidence"][0]["stage"] == case["stage"]
                     and case["sanitized_excerpt"] in diagnosis["evidence"][0]["content"])
    passed = proc.returncode == 0 and not missing and preserved
    receipt = dict(scenario=case["id"], status="passed" if passed else "failed", exit_code=proc.returncode,
                   missing_markers=missing, evidence_preserved=preserved, diagnosis=diagnosis,
                   mode="managed Azure embeddings and GPT-4o" if managed else "local deterministic; no Azure requests", manifest_sha256=sha(manifest_path.read_bytes()),
                   source=case, input_sha256=sha(log), envelope_sha256=sha(envelope),
                   correlation_revision=git(REPO, "rev-parse", "HEAD").decode().strip(),
                   correlation_diff_sha256=sha(git(REPO, "diff", "HEAD")),
                   peer_revisions=manifest["peer_revisions"])
    if managed:
        receipt["citation_validation_accepted"] = any(line.startswith("PIPESOR_FOUNDRY_VALIDATION ") for line in lines)
        receipt["scope_probe"] = next((line for line in lines if line.startswith("PIPESOR_RAG_HTTP ")), None)
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    files = ("log.txt", "envelope.json", "owner-chain.log", "receipt.json")
    if managed:
        files += tuple(path.name for path in sorted(out.glob("*attempt-*.json")))
    (out / "SHA256SUMS").write_text("".join(f"{sha((out / n).read_bytes())}  {n}\n" for n in files))
    print(receipt["status"], out)
    return int(not passed)


if __name__ == "__main__":
    raise SystemExit(main())
