# Preliminary CI/CD scenario suite v1.1.0

Section 4 evidence package for the local feasibility of Pipesor. The [manifest](../tools/preliminary/manifest.v1.1.json) pins peer revisions, both input hashes per case, the pre-run references, and expected observations. The [v1.0.0 release record](preliminary-validation.md) retains its original receipt and the status of the ten external candidates. This increment uses only self-authored fictional data.

## Scenario matrix

| Case | Input and pre-run reference | Expected behavior |
| --- | --- | --- |
| E01 · Checkout DNS | Runner log records failure to resolve `git.example.invalid`; the underlying reason for failed DNS remains unknown. The baseline manifest ID is `local-checkout-dns`. | Preserve the infrastructure-stage error, cite the log and scoped runner guide, and return the diagnosis over authenticated HTTP. |
| E02 · Dependency resolution | Authored GitHub Actions-style BUILD log and v2 evidence envelope. The fictional offline index was defined to contain `org.example:fixture-lib:1.0.0` only; the fixture requests `9.9.9`. That establishes the reason for the unresolved dependency within this synthetic setup. | Preserve the BUILD stage and exact coordinate, retrieve one generic dependency guide from the authorized scope, and cite the log and that chunk. |
| E03 · Ambiguous build failure | Authored GitHub Actions-style BUILD log and v2 envelope retaining exit code 1. The command and stderr were omitted at fixture creation. A specific cause cannot be established from this input. | Preserve the BUILD stage and exit status, state the missing information, cite the log, and return zero knowledge citations. The same retrieval probe must find the seeded guide in its own scope and return no match in the incident's scope. |

## Method and environment

Inputs retain separate workflow run IDs and correlation IDs. Each run binds the line to a synthetic GitHub repository, installation, tenant and canonical pipeline under the declared scope revision. The owner-chain harness stages the protected evidence and execution fixtures, sends the log through the NLP processor and signed correlation intake, checks durable Recommendation receipt provenance, then queries RAG through its existing HTTP contract. Recommendation validates the evidence and chunk identifiers before the test-authenticated HTTP response is compared with the resulting diagnosis. The incident inputs contain fictional values and require no upstream log access. The harness supplies test credentials and synthetic scope decisions.

E02 and E03 use a disposable PostgreSQL 17/pgvector container pinned by digest in the [owner-chain script](../tools/verify_local_owner_chain.sh), with the actual RAG importer, retrieval API and Alembic revision `20261116_03`. Embeddings are deterministic 1536-dimensional vectors (`simulated-v1`). The generic corpus describes dependency-resolution checks and omits fixture coordinates, incident logs and reference labels. E02 seeds that guide in the incident's authorized scope. E03 seeds it under a separate scope revision to serve as a positive control for the authorization filter. The fixture inference tests the Recommendation contract with controlled hypotheses. Its outcomes cannot establish the behavior of GPT-4o on E02 or E03.

The local execution environment used OpenJDK 25.0.5.0.3, Python 3.14.8, Maven Wrapper and Docker Engine 29.9.3. Historical real-AI evidence for the E01 input covers Azure `text-embedding-3-small` v1 (`pipesor-dev-embeddings`), PostgreSQL/pgvector, and GPT-4o `2024-11-20` (`pipesor-dev-diagnostics`). The earlier receipt is separate from this increment.

## Reproduce and retain evidence

Follow the [local diagnostic guide](local-diagnostic-demo.md) for Java 25, Docker and a Python environment with both NLP and RAG dependencies. Run from the workspace root:

```bash
runner=services/pipesor-evidence-correlation-engine/tools/preliminary/run.py
export PYTHON_BIN=/path/to/hybrid-venv/bin/python
out="$(mktemp -d)"
python3 "$runner" verify
export PIPESOR_PLAYWRIGHT_MODULE=/path/to/node_modules/playwright
python3 "$runner" run local-checkout-dns --evidence "$out/E01" \
  --browser pipesor-frontend/tools/verify_local_diagnostic.cjs
python3 "$runner" run E02 --evidence "$out/E02"
python3 "$runner" run E03 --evidence "$out/E03"
for case in E01 E02 E03; do
  (cd "$out/$case" && sha256sum -c SHA256SUMS)
done
```

The runner checks the source input hashes before execution and strips managed-provider configuration. It writes a log, structured receipt and checksums to each fresh output directory. Preserve the first failure's log, receipt and source hashes if a test fails. For successful cases, preserve the diagnosis with its context, execution, correlation and cited evidence IDs, the retrieval status/chunk ID, and the authenticated HTTP results. The credential and scope tests cover a wrong RAG caller, cross-scope retrieval, and diagnosis HTTP rejection. The fixture guards also check no-match, invalid citations and an unsupported source suggested by injected text. No managed requests are authorized by the commands above. A managed run requires separate authorization and available budget, plus a receipt identifying the deployment/model versions, provider status and usage, and managed citation outcomes.

## Observed outcomes and manuscript properties

| Case | Executed result | P1 | P2 | P3 |
| --- | --- | --- | --- | --- |
| E01 | Deterministic v1.1 replay passed with one checkout log citation, one fixture guide and authenticated React in English and Spanish. Earlier managed integration also passed for this input. | Partially supported | Partially supported by the earlier local managed scope probes | Pending |
| E02 | Deterministic owner chain passed. The authenticated diagnosis cited the original unresolved coordinate and one authorized guide. The alternate scope returned no match and the wrong caller received HTTP 403. | Partially supported | Partially supported | Pending |
| E03 | Deterministic owner chain passed. The authenticated diagnosis cited the exit-status evidence, recorded the missing command and stderr, and made no knowledge citation. The guide was retrievable in its own scope and absent from the incident scope. The wrong caller received HTTP 403. | Partially supported | Partially supported | Pending |

P1 is **“cada diagnóstico puede reconstruirse a partir de su ejecución, evidencia y fuentes”**. The recorded fixture, normalized evidence, execution ID, context and validated citations support reconstruction for these instrumented local runs. E03 explicitly has no eligible knowledge source. Production reconstruction and external replay remain unverified.

P2 is **“una consulta RAG no obtiene fragmentos fuera del alcance autorizado”**. The local RAG API checked caller restrictions and PostgreSQL scope filtering with synthetic scope decisions. Production Entra bindings and broader authorization coverage remain unverified.

P3 is **“la revisión humana distingue hipótesis generada de diagnóstico confirmado, corregido o rechazado”**. An actual review transition was outside these runs, so P3 remains pending. These results support local architectural feasibility; statistical or comparative conclusions require a separate study.

## Evidence inventory

Receipts and logs are in `~/.local/state/pipesor-preliminary-validation/`; each v1.1 attempt has its own `SHA256SUMS`. All three v1.1 runs passed on their first attempt, with one owner-chain integration test each.

| Evidence directory | Receipt SHA-256 |
| --- | --- |
| `v1-20261119` | Original bundle. Its checksums were reverified. |
| `v1.1-E01-attempt-1` | `0112df54033981e387f3341cadd9bdb44b9c13ac70a74cff0f8ef771ff41db8e` |
| `v1.1-E02-attempt-1` | `26d0bd0315228fdf9bfb67226ebb07969a08c4802c8cb54694f28b5895222745` |
| `v1.1-E03-attempt-1` | `f7d57e7006135a737a9132858000f91b9d69fb26030a29fc1ccdf9ffbdcf3fb0` |

Historical real AI receipts for E01 are the protected `real-diagnostic-integration/verification-summary.json` and `owner-chain-verified.log` under `~/.local/state/pipesor-dev-bootstrap/`. E02 and E03 used zero Azure embedding or chat requests. Their real-model behavior remains pending separate scope and budget authorization.
