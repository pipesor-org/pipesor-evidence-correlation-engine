# Integrated diagnostic core · Local verification

> **Historical record.** The merged-classpath harness this page uses (`tools/local-handoff`, `tools/verify_local_*.sh`, `tools/preliminary/run.py` and `external.py`) was retired on 2026-11-22 when the services became real runtimes (ADR-022). Its results stay valid as dated evidence; reproduce them from commit `25ad9c7` of this repository. Scenario manifests and fixtures under `tools/preliminary/` are kept.

## Boundaries and traceability

- Main already contains Recommendation PR #10 and Frontend PR #21. Their GitHub checks passed. New work branches from those merged revisions.
- Recommendation's `PersistedDiagnosticWorkflow` is the explicitly composed entry point after correlated inputs have been authorized and reconstructed. It calls the existing `DiagnosticOrchestrator`. Validated output is admitted to Recommendation PostgreSQL before it is returned. The orchestrator's citation checks are unchanged. Invalid output leaves no diagnosis snapshot.
- The stable local diagnosis ID is derived from the correlated context ID. The fingerprint covers the authorized scope, retrieval query, execution and incident IDs, and admitted evidence. Exact committed replay returns the stored output and version without retrieving knowledge or invoking inference. A different fingerprint for that diagnosis ID conflicts. Concurrent first attempts can both infer before committing. PostgreSQL serializes per-incident version allocation and the first committed output wins for identical concurrent inputs.
- Snapshots preserve the original hypothesis, evidence IDs and excerpts, RAG citations and provenance, limitations, and correlation context. The existing review API reads that stored version. Review revisions are appended independently. The separate presentation of generated hypothesis and reviewed decisions implements the local P3 distinction.
- The integration exercises the Recommendation ownership of ADR-016/017 and the append-only review semantics of DD-005. It preserves ADR-020's separate authorization decision. The fixture binds its ephemeral reviewer to the admitted scope and denies another caller. Production current-grant resolution and the production REST/read projection remain PD-011/034 integration work. [Architecture decision index](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/index.md) · [Domain decisions](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md)

## Reproduce

The E02 scenario uses real local normalization, correlation, RAG HTTP, PostgreSQL/pgvector, citation validation, admission and review APIs. Embeddings and inference are deterministic. The GitHub-shaped source fixture, evidence and references come from the checksummed synthetic scenario manifest. The browser performs three successive decisions on the same E02 version to exercise history. These are scripted test decisions, and their sequence is a lifecycle test rather than an independent human adjudication.

Use the Python and Docker setup in the [baseline guide](local-diagnostic-demo.md) and the frontend's Node/Bun prerequisites. Run from the workspace root. The Recommendation revision must be a locally available commit containing durable admission. Checkout the matching frontend revision so the browser helper can exercise successive decisions.

```bash
umask 077
out="$(mktemp -d)"
export PYTHON_BIN=/path/to/hybrid-venv/bin/python
npm install --prefix "$out/browser" --no-save playwright
"$out/browser/node_modules/.bin/playwright" install chromium
export PIPESOR_PLAYWRIGHT_MODULE="$out/browser/node_modules/playwright"
rec_ref="$(git -C services/pipesor-recommendation-service rev-parse HEAD)"
python3 -B services/pipesor-evidence-correlation-engine/tools/preliminary/run.py run E02 \
  --review-recommendation-ref "$rec_ref" \
  --browser pipesor-frontend/tools/verify_local_review.cjs \
  --evidence "$out/E02"
(cd "$out/E02" && sha256sum -c SHA256SUMS)
(
  cd services/pipesor-recommendation-service
  PIPESOR_REVIEW_RECEIPT="$out/restart-receipt.json" ./mvnw -B -ntp verify
)
```

Set `PIPESOR_CHROMIUM_EXECUTABLE` when selecting a compatible preinstalled Chromium. Historical harness invocations without the review switch retain their pinned Recommendation revisions. The review switch rejects managed inference.

The separate Recommendation test replays a sanitized E05 test copy through the citation validator and the admission wrapper, then checks exact replay and immutable reconstruction across an actual Spring application-context restart against the same PostgreSQL database. It also checks concurrent admission versions, invalid and conflicting admission, duplicate and conflicting reviews, and access denial. The E05 experiment and its checksummed source evidence remain untouched.

## Observed results

The integrated E02 test passed on 2026-11-19 with one automatically admitted version. The existing authenticated diagnostic route returned the same output, and the review API reconstructed it without an in-memory copy. Chromium recorded CONFIRMED in English at 1280 px, CORRECTED in Spanish at 390 px, and REJECTED in English at 1280 px. It reloaded each history, checked the immutable hypothesis, and verified clearing on HTTP 401. A final authorized read and PostgreSQL query found all three ordered review rows. Wrong-scope read returned 404. The RAG API returned the scoped source, no source in the alternate scope and HTTP 403 for the wrong caller. Exact diagnosis replay did not call RAG or inference.

Recommendation's 23-test suite passed. It verified E05 replay admission and immutable readback after service restart, concurrent version assignment, no row on invalid citations, conflicting fingerprint rejection, and one winner for concurrent review writes. The E02 owner-chain host and the separately restarted Recommendation app have distinct receipts. No new Azure requests were made.

The local core is demonstrated. The harness explicitly invokes the workflow once the verified Correlation receipt is available. Production still needs the approved runtime trigger, current-scope authorization and read reconstruction, formal user acceptance, and deployment verification.
