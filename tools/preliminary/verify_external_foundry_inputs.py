"""Verify the two Increment 5 inputs offline. This command has no provider path."""

import argparse
import csv
import io
import json
from pathlib import Path
from xml.etree import ElementTree
from zipfile import ZipFile

from external import HERE, prepare
from run import require, sha

BUNDLE_SHA = "0ec2e5fdc1a5bfc515e3ecd11fc990905838b15752b5b908147f6b46b8c65633"
CASES = {
    "E05": ("566824752", "fatal error: 'libproc.h' file not found"),
    "E06": ("549434310", 'The command "$TIMEOUT 35m ci/build.sh" exited with 124.'),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        require(sha(args.bundle.read_bytes()) == BUNDLE_SHA, "Increment 4 bundle hash differs")
        with ZipFile(args.bundle) as bundle, ZipFile(args.archive) as archive:
            entries = dict(line.split("  ", 1)[::-1] for line in bundle.read("SHA256SUMS").decode().splitlines())
            require(len(entries) == 11 and set(entries) == set(bundle.namelist()) - {"SHA256SUMS"},
                    "Bundle checksum inventory differs")
            require(all(sha(bundle.read(name)) == digest for name, digest in entries.items()),
                    "Bundle file checksum differs")
            manifest_bytes = bundle.read("manifest.json")
            require(manifest_bytes == (HERE / "external-manifest.v1.json").read_bytes(), "Working manifest differs")
            manifest = json.loads(manifest_bytes)
            dataset = manifest["dataset"]
            require(sha(args.archive.read_bytes()) == dataset["archive_sha256"], "Source archive hash differs")
            crossvalidation = archive.read(dataset["crossvalidation_member"]).decode("utf-8-sig")
            rows = list(csv.DictReader(io.StringIO(crossvalidation)))
            for scenario, (build, reviewed) in CASES.items():
                case = next(case for case in manifest["cases"] if case["id"] == scenario)
                require((case["build_id"], case["sanitized_excerpt"]) == (build, reviewed),
                        f"{scenario}: reviewed data changed")
                log, envelope = prepare(archive, case, dataset, manifest["peer_revisions"])
                require((log, envelope) == (bundle.read(f"{scenario}/log.txt"), bundle.read(f"{scenario}/envelope.json")),
                        f"{scenario}: replay inputs differ")
                raw_line = archive.read(case["log_member"]).split(b"\n")[case["line"] - 1]
                require(reviewed.encode() in raw_line, f"{scenario}: source excerpt differs")
                xml = ElementTree.fromstring(archive.read(case["annotation_member"]))
                chunks = [entry.findtext("Chunk") or "" for entry in xml.findall("Example")
                          if ("LogChunks/logs/" + (entry.findtext("Log") or "")) == case["log_member"]]
                require(len(chunks) == 1 and reviewed in chunks[0], f"{scenario}: annotation differs")
                confirmations = [i for i, row in enumerate(rows, 2) if row["build_id"] == build
                                 and row["extraction valid"] == "true" and not row["valid - override"]]
                require(confirmations, f"{scenario}: no direct developer confirmation")
                print(f"{scenario}: Travis build {build}, source line {case['line']}, CSV row {confirmations[0]}; "
                      f"log SHA-256 {sha(log)}; envelope SHA-256 {sha(envelope)}")
            print(f"Verified {len(entries)} export checksums and both source mappings. No inference requests.")
    except (ValueError, OSError) as error:
        parser.exit(2, f"{error}\n")


if __name__ == "__main__":
    main()
