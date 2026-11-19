# Local diagnostic demonstration

## Run

From the directory containing the sibling service checkouts, use Java 25,
Docker with Testcontainers access, and Python 3.14 with the NLP and RAG projects' pinned
dependencies installed.

```bash
python3.14 -m venv /tmp/pipesor-demo-nlp
/tmp/pipesor-demo-nlp/bin/python -m pip install ./pipesor-nlp-log-processor ./pipesor-rag-retrieval
export PYTHON_BIN=/tmp/pipesor-demo-nlp/bin/python
set -o pipefail
bash pipesor-evidence-correlation-engine/tools/verify_local_owner_chain.sh 2>&1 | tee /tmp/pipesor-diagnostic-demo.log
```

The runner archives committed local `main` revisions of Pipeline, Log/Event,
NLP, RAG and Recommendation and prints their commit IDs. Set
`PIPESOR_DEMO_RECOMMENDATION_REF` or `PIPESOR_DEMO_RAG_REF` to test a different
committed revision of either service.
Correlation builds from the current checkout. Maven and Docker may need to
download dependencies and images on the first run. PostgreSQL 17.11 is disposable.

## Verify the frontend over HTTP

From the workspace root, with the NLP/RAG Python environment above, frontend
Bun dependencies installed, and Node available:

```bash
npm install --prefix /tmp/pipesor-browser-check --no-save playwright
/tmp/pipesor-browser-check/node_modules/.bin/playwright install chromium
export PIPESOR_PLAYWRIGHT_MODULE=/tmp/pipesor-browser-check/node_modules/playwright
export PIPESOR_DEMO_BROWSER_CHECK="$PWD/pipesor-frontend/tools/verify_local_diagnostic.cjs"
set -o pipefail
bash services/pipesor-evidence-correlation-engine/tools/verify_local_owner_chain.sh 2>&1 | tee /tmp/pipesor-diagnostic-http.log
```

The harness generates a one-run bearer token, binds Recommendation's explicit
GET route to the test host's loopback listener, and passes the token to the
browser check in its environment. Vite proxies the single route on the same
origin. The route returns the actual result the orchestrator composed from the
committed receipt and local RAG response. It keeps responses out of HTTP caches.
Look for `PIPESOR_DIAGNOSTIC_HTTP` and `PIPESOR_DIAGNOSTIC_BROWSER` in the log.
The default Recommendation app and the production React router leave the demo
route unregistered. This fixture exercises local bearer authentication while the
dashboard read contract and Entra authorization remain open.

## Inspect one scenario

1. Open [checkout-failure.log](../tools/local-handoff/checkout-failure.log). The
   synthetic GitHub Actions checkout reports `Could not resolve host: git.example.invalid`.
2. Find `PIPESOR_DIAGNOSIS` in the log. Compare `evidence[0].content` with the
   fixture. The test reads that evidence from the committed Recommendation
   receipt and verifies its evidence ID against the registered source event.
   Compare `contextId` with `originContextId`, and follow `correlationId`
   through the evidence envelope, receipt and RAG client request.
3. Find the RAG server's `http_request_completed` record with HTTP 200 and the
   same correlation ID. Read the probable cause, suggested check and limitation.
   Match the cited chunk and document IDs to the synthetic knowledge fixture in
   [serve_rag_fixture.py](../tools/local-handoff/serve_rag_fixture.py). Confirm that
   Maven reports `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and
   `BUILD SUCCESS`. Context IDs can differ across runs.

The run completed on 2026-11-18 with Java 25.0.5.0.3 and Docker 29.9.3.
It returned **“The checkout runner could not resolve the Git remote hostname.”**
The recommendation was to check name lookup and network configuration from the
affected runner. The result leaves the cause of the name-resolution failure open.

| Observed output field | Value |
| --- | --- |
| Correlation | `ci-investigation-204` |
| Execution | `9306afb1-dafe-5915-bded-b9ba5237985a` |
| Context and evidence origin | `05150e8c-d6e0-4d17-9089-540a3acd0864` |
| Evidence | `e6988490-6322-590d-b48d-662b154b55d9`, [checkout fixture, line 1](../tools/local-handoff/checkout-failure.log#L1) |
| Knowledge provenance | `fixture:runner-network-checks`, chunk `44444444-4444-4444-4444-444444444444`, document `55555555-5555-5555-5555-555555555555` |

The test also exercises owner denial, a separate run attempt, duplicate
delivery, and refusal to emit a diagnosis for missing output, unsupported
evidence and knowledge citations, and dependency failures.
Separate `./mvnw -B -ntp verify` runs passed 14 Recommendation tests and 87
Correlation tests.

## Validation scope and blockers

| Status | Capability |
| --- | --- |
| Implemented and executed | Recommendation orchestrates the existing RAG client and an injected inference provider, checks cited IDs, and composes probable cause, recommendation, limitations and provenance. |
| Integrated and executed locally | NLP sanitization and Drain3 parsing, signed loopback NLP/Correlation and owner calls, correlation and Recommendation receipt over HTTP, PostgreSQL persistence, test-only receipt readback, and the real Recommendation client and RAG API over loopback HTTP. |
| Simulated | Pre-registered evidence and GitHub run data, current-scope decisions and credentials, RAG local caller authentication, in-memory synthetic corpus and deterministic embeddings, and deterministic inference based on the observed log line. The replay assertion substitutes a changed parser template. |
| Pending managed AI | Verify the RAG production storage path (pgvector), authorized corpus, Entra/TLS and managed embedding deployment. Confirm the diagnostic model variant (PD-026), compatible Azure OpenAI endpoint and effective grants before live inference. |
| Pending runtime | Live GitHub capture, Service Bus settlement, live workload authorization, deployment, and an approved diagnostic query/read model (PD-011), diagnosis persistence, review and workflow access (PD-036). |

A domain specialist can judge whether the hypothesis and manual check follow
from the source line, whether the output makes its uncertainty clear, and
whether an evaluator can trace the diagnosis through the receipt.
Managed model accuracy, retrieval relevance on an authorized corpus and
production readiness remain unverified.
