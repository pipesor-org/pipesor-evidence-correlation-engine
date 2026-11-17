#!/usr/bin/env python3
"""Check approved mapping vectors; this reference checker is not a receiver."""
import copy
from datetime import datetime
import hashlib
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

ROOT = Path(__file__).resolve().parents[1] / "docs/contracts"


def object_without_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def reject_constant(_):
    raise ValueError("Non-JSON number")


def load_json(text):
    return json.loads(text, object_pairs_hook=object_without_duplicates,
                      parse_constant=reject_constant)


def canonical_bytes(body):
    return json.dumps(body, sort_keys=True, separators=(",", ":"), ensure_ascii=False,
                      allow_nan=False).encode("utf-8")


def main():
    spec = load_json((ROOT / "correlation-recommendation-v1.candidate.json").read_text())
    vectors = load_json((ROOT / "correlation-recommendation-v1.examples.json").read_text())
    assert spec["openapi"] == "3.1.0"
    assert spec["x-pipesor-state"] == "mapping-approved-activation-blocked"
    operation = spec["paths"]["/internal/v1/correlated-contexts"]["post"]
    assert operation["security"] == [{"entraBearer": []}]
    headers_required = {p["name"] for p in operation["parameters"] if p["required"]}
    assert headers_required == {"Idempotency-Key", "X-Correlation-ID"}
    assert operation["responses"]["200"]["content"]["application/json"]["schema"] == {
        "$ref": "#/components/schemas/Receipt"}
    assert spec["x-pipesor-pending-decisions"] and "servers" not in spec
    schemas = json.loads(json.dumps(spec["components"]["schemas"]).replace(
        "#/components/schemas/", "#/$defs/"))
    for schema in schemas.values():
        Draft202012Validator.check_schema(schema)
    validators = {name: Draft202012Validator({"$defs": schemas, "$ref": f"#/$defs/{name}"},
                                             format_checker=FormatChecker())
                  for name in ("Submission", "Receipt")}

    def submission(body, headers):
        validators["Submission"].validate(body)
        assert body["operation_id"] == body["context"]["id"] == headers["Idempotency-Key"]
        assert body["context"]["correlation_id"] == headers["X-Correlation-ID"]
        datetime.strptime(body["context"]["created_at"], "%Y-%m-%dT%H:%M:%S.%fZ")
        members = body["context"]["evidence_ids"]
        signal_ids = [signal["evidence_id"] for signal in body["signals"]]
        assert members == sorted(members)
        assert len(signal_ids) == len(set(signal_ids)) and set(signal_ids) == set(members)
        origins = [signal["origin_context_id"] for signal in body["signals"]]
        assert len(origins) == len(set(origins))
        assert origins[-1] == body["context"]["id"]
        assert body["signals"][-1]["correlation_id"] == body["context"]["correlation_id"]
        canonical_bytes(body)  # Reject unpaired surrogates before comparison.

    def receipt(body, sent):
        validators["Receipt"].validate(body)
        assert body["operation_id"] == sent["operation_id"]
        assert body["context_id"] == sent["context"]["id"]

    def rejected(check):
        try:
            check()
        except (AssertionError, ValidationError, ValueError, UnicodeError):
            return
        raise AssertionError("Invalid synthetic example was accepted")

    for fixture in vectors["fixtures"]:
        body, headers = fixture["submission"], fixture["headers"]
        submission(body, headers)
        receipt(fixture["accepted_receipt"], body)
        receipt(fixture["duplicate_receipt"], body)
        canonical = canonical_bytes(body)
        assert canonical == fixture["proposed_canonical_json"].encode("utf-8")
        assert "v1:sha256:" + hashlib.sha256(canonical).hexdigest() == fixture["proposed_fingerprint"]
        reordered = load_json(json.dumps(body, indent=4, ensure_ascii=True))
        reordered = dict(reversed(list(reordered.items())))
        assert canonical_bytes(reordered) == canonical
        for field in ("text", "scope", "trace"):
            changed = copy.deepcopy(body)
            if field == "text":
                changed["signals"][0]["content"] += " "
            elif field == "scope":
                changed["authorization_scope"]["scope_revision"] += "-changed"
            else:
                changed["context"]["correlation_id"] += "-changed"
                changed["signals"][-1]["correlation_id"] = changed["context"]["correlation_id"]
            changed_headers = dict(headers, **{"X-Correlation-ID": changed["context"]["correlation_id"]})
            submission(changed, changed_headers)
            assert canonical_bytes(changed) != canonical

    a, ab = [fixture["submission"] for fixture in vectors["fixtures"]]
    assert a["signals"][0] == ab["signals"][0]
    headers = vectors["fixtures"][1]["headers"]
    for kind in ("crossed", "duplicate", "blank", "unknown", "timestamp", "surrogate", "nul", "operation",
                 "revision-newline", "installation-newline", "repository-newline", "correlation-newline",
                 "uuid-newline", "timestamp-newline"):
        changed = copy.deepcopy(ab)
        changed_headers = dict(headers)
        if kind == "crossed":
            changed["signals"][0]["evidence_id"] = changed["context"]["id"]
        elif kind == "duplicate":
            changed["signals"][1]["evidence_id"] = changed["signals"][0]["evidence_id"]
        elif kind == "blank":
            changed["signals"][0]["content"] = "\n\t "
        elif kind == "unknown":
            changed["raw_body"] = "synthetic forbidden extra field"
        elif kind == "timestamp":
            changed["context"]["created_at"] = "2026-02-31T00:00:00.000001Z"
        elif kind == "surrogate":
            changed["signals"][0]["content"] = "\ud800"
        elif kind == "nul":
            changed["signals"][0]["content"] = "protected\u0000text"
        elif kind == "operation":
            changed["operation_id"] = a["operation_id"]
        elif kind == "revision-newline":
            changed["authorization_scope"]["scope_revision"] += "\n"
        elif kind == "installation-newline":
            changed["authorization_scope"]["github_installation_id"] += "\n"
        elif kind == "repository-newline":
            changed["authorization_scope"]["github_repository_id"] += "\n"
        elif kind == "correlation-newline":
            changed["context"]["correlation_id"] += "\n"
            changed_headers["X-Correlation-ID"] = changed["context"]["correlation_id"]
        elif kind == "uuid-newline":
            changed["context"]["incident_id"] += "\n"
        else:
            changed["context"]["created_at"] += "\n"
        rejected(lambda: submission(changed, changed_headers))
    for field in ("duplicate-origin", "latest-origin", "latest-trace"):
        changed = copy.deepcopy(ab)
        if field == "duplicate-origin":
            changed["signals"][0]["origin_context_id"] = changed["signals"][1]["origin_context_id"]
        elif field == "latest-origin":
            changed["signals"][-1]["origin_context_id"] = changed["signals"][-1]["evidence_id"]
        else:
            changed["signals"][-1]["correlation_id"] = "synthetic-crossed-trace"
        rejected(lambda: submission(changed, headers))
    reversed_signals = copy.deepcopy(ab)
    reversed_signals["signals"].reverse()
    assert canonical_bytes(reversed_signals) != canonical_bytes(ab)
    rejected(lambda: receipt(vectors["wrong_receipt"], ab))
    for invalid in ({}, None, dict(vectors["fixtures"][1]["accepted_receipt"], status="diagnosed")):
        rejected(lambda: receipt(invalid, ab))
    rejected(lambda: submission(ab, dict(headers, **{"X-Correlation-ID": "alias-message-trace"})))
    rejected(lambda: load_json('{"operation_id":"a","operation_id":"b"}'))
    rejected(lambda: load_json('{"value":NaN}'))
    rejected(lambda: load_json(""))
    rejected(lambda: load_json("<html>synthetic error</html>"))
    rejected(lambda: load_json("\ufeff{}"))
    print("Mapping checks passed: schemas, A/A+B, receipts, negative shapes and comparison vectors.")
    print("No HTTP, persistence, authorization, retry lifecycle or complete OpenAPI validation was exercised.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, ValidationError, ValueError, UnicodeError, KeyError):
        raise SystemExit("Mapping check failed; inspect local synthetic vectors (payload omitted).") from None
