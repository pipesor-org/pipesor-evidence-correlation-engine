# Local diagnostic demonstration

> **Historical record.** The merged-classpath harness this page uses (`tools/local-handoff`, `tools/verify_local_*.sh`, `tools/preliminary/run.py` and `external.py`) was retired on 2026-11-22 when the services became real runtimes (ADR-022). Its results stay valid as dated evidence; reproduce them from commit `25ad9c7` of this repository. Scenario manifests and fixtures under `tools/preliminary/` are kept.

For the interactive registered-workflow journey, use the [registered E02 advisor run](registered-e02-advisor.md).

For the manuscript property mapping and planned external cases, see the
[preliminary scenario suite](preliminary-validation.md).

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

## Local-first development with managed embeddings

Use the same harness with a disposable, digest-pinned PostgreSQL 17/pgvector
container. It enables `vector`, runs the RAG Alembic migrations, then imports one
generic synthetic runner guide with the existing corpus importer. Its scope is
derived from the synthetic evidence envelope and its provenance is
`synthetic://pipesor/local-dev/runner-connectivity/v1#chunk=0`. The guide is
development data. Keep formal evaluation cases and reference answers out of it.

Use an existing authorized Azure CLI session. Only the synthetic guide and test
queries are sent to the approved embedding endpoint. The harness uses
`DefaultAzureCredential` with the local CLI session, binds RAG and PostgreSQL to
loopback, and discards the database at exit. Test caller authentication is local.

```bash
export PIPESOR_DEMO_RAG_MODE=hybrid
export PIPESOR_RAG_AZURE_OPENAI_ENDPOINT=https://ai-pipesor-dev-04f339b7.services.ai.azure.com
export PIPESOR_RAG_AZURE_OPENAI_DEPLOYMENT=pipesor-dev-embeddings
export PIPESOR_RAG_EMBEDDING_MODEL_VERSION=1
bash services/pipesor-evidence-correlation-engine/tools/verify_local_owner_chain.sh
```

Run that command from the workspace root with the Python environment described
above. Add the browser variables below for the same run to verify the React view.
The expected markers are `PIPESOR_RAG_IMPORT` for the stored 1536-dimensional
vector and `PIPESOR_RAG_HTTP` for the authorized result, scope isolation and
caller rejection. Recommendation uses its existing HTTP client and produces an
evidence-backed result with managed retrieval. The inference provider in this
harness is a labeled deterministic fixture.

The 2026-11-19 run verified PostgreSQL 17 with Alembic `20261116_03`, successful
Foundry embedding requests via `AzureCliCredential`, an authorized result with
stored provenance, a wrong-scope empty result, and HTTP 403 for a wrong caller.
The authenticated Recommendation HTTP path returned the generated diagnosis.
Chromium rendered the same result with provenance in English at 1280px and
Spanish at 390px and cleared it after HTTP 401. These are integration checks
on synthetic development inputs; academic validation remains pending.

## Opt in to real GPT-4o diagnostics

Use the same local-first command with `PIPESOR_DEMO_LLM_MODE=foundry` and a
protected mode-700 directory for request receipts. The Foundry test calls the
existing DEV `pipesor-dev-diagnostics` deployment, GPT-4o `2024-11-20`, at the
same account-level `services.ai.azure.com` origin. It uses the local Azure CLI
operator for Entra, the existing Recommendation inference port, and the same
test-only HTTP routes. Cloud application and interactive Entra login remain pending.

From the workspace root, after performing the Python and browser dependency
setup in this guide, run:

```bash
export PYTHON_BIN=/tmp/pipesor-demo-nlp/bin/python
export PIPESOR_DEMO_RAG_MODE=hybrid PIPESOR_DEMO_LLM_MODE=foundry
export PIPESOR_DEMO_RAG_REF=1772c18e2b31d228ee05a82a2b145827930baebd
export PIPESOR_DEMO_RECOMMENDATION_REF=aa8ce3b
export PIPESOR_RAG_AZURE_OPENAI_ENDPOINT=https://ai-pipesor-dev-04f339b7.services.ai.azure.com
export PIPESOR_RAG_AZURE_OPENAI_DEPLOYMENT=pipesor-dev-embeddings
export PIPESOR_RAG_EMBEDDING_MODEL_VERSION=1
export PIPESOR_PLAYWRIGHT_MODULE=/tmp/pipesor-browser-check/node_modules/playwright
export PIPESOR_DEMO_BROWSER_CHECK="$PWD/pipesor-frontend/tools/verify_local_diagnostic.cjs"
export PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR="$HOME/.local/state/pipesor-real-diagnostics"
install -d -m 700 "$PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR"
set -o pipefail
bash services/pipesor-evidence-correlation-engine/tools/verify_local_owner_chain.sh   2>&1 | tee "$PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR/owner-chain.log"
```

Set the Recommendation ref to the equivalent reviewed commit if the branch
has been integrated. Use only the published synthetic scenario and corpus.
Keep the same receipt directory for all attempts in an authorization window.
The fixture makes no automatic chat retries, limits the request to 8,000 bytes
and 1,024 output tokens, and stops at eight request slots. It spaces the two
cases across rate-limit windows. Check `PIPESOR_FOUNDRY_VALIDATION` for the
matched and evidence-only outcomes and `PIPESOR_DIAGNOSTIC_BROWSER` for React.
The receipt files contain Azure HTTP status, model, request ID and token usage.
They contain no prompts, tokens or raw model responses.

On 2026-11-19, the matched and no-match cases both returned HTTP 200 from
`gpt-4o-2024-11-20`. Recommendation accepted the scoped evidence and chunk
citations for the matched case. The no-match case preserved the authorized
scope, requested an empty corpus kind, and produced an evidence-only result.
The authenticated local HTTP endpoint delivered the matched diagnosis to
Chromium in both supported locales. The verification used 6 chat
requests including the historical replays and the earlier matched pass.
It used 2620 chat tokens and approximately USD 0.0141
at the regional retail rates. The original failed response fields were
unavailable and three instrumented replays did not reproduce its failure.

Without the Foundry opt-in, the default owner-chain path uses deterministic
inference. Cloud workload activation, interactive Entra authentication and
formal validation remain separately pending.

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

1. Open [checkout-failure.log](https://github.com/pipesor-org/pipesor-evidence-correlation-engine/blob/25ad9c7/tools/local-handoff/checkout-failure.log). The
   synthetic GitHub Actions checkout reports `Could not resolve host: git.example.invalid`.
2. Find `PIPESOR_DIAGNOSIS` in the log. Compare `evidence[0].content` with the
   fixture. The test reads that evidence from the committed Recommendation
   receipt and verifies its evidence ID against the registered source event.
   Compare `contextId` with `originContextId`, and follow `correlationId`
   through the evidence envelope, receipt and RAG client request.
3. Find the RAG server's `http_request_completed` record with HTTP 200 and the
   same correlation ID. Read the probable cause, suggested check and limitation.
   Match the cited chunk and document IDs to the synthetic knowledge fixture in
   [serve_rag_fixture.py](https://github.com/pipesor-org/pipesor-evidence-correlation-engine/blob/25ad9c7/tools/local-handoff/serve_rag_fixture.py). Confirm that
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
| Evidence | `e6988490-6322-590d-b48d-662b154b55d9`, [checkout fixture, line 1](https://github.com/pipesor-org/pipesor-evidence-correlation-engine/blob/25ad9c7/tools/local-handoff/checkout-failure.log#L1) |
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
| Simulated | Pre-registered evidence and GitHub run data, current-scope decisions and credentials, RAG local caller authentication, in-memory synthetic corpus and deterministic embeddings, and deterministic inference based on the observed log line. The replay assertion substitutes a changed parser template. Hybrid RAG uses PostgreSQL and managed embeddings. The separate Foundry opt-in uses real GPT-4o inference. |
| Verified real managed AI, local runtime | Azure embeddings, PostgreSQL/pgvector, real GPT-4o matched and evidence-only validation, authenticated HTTP and React. |
| Pending cloud and academic validation | Cloud workload identity, tenant bindings, deployment, production corpus authorization, and formal evaluation. Revisit the PD-026 model before the next cycle. |
| Pending runtime | Live GitHub capture, Service Bus settlement, live workload authorization, deployment, and an approved diagnostic query/read model (PD-011), diagnosis persistence, review and workflow access (PD-036). |

A domain specialist can judge whether the hypothesis and manual check follow
from the source line, whether the output makes its uncertainty clear, and
whether an evaluator can trace the diagnosis through the receipt.
Managed model accuracy, retrieval relevance on an authorized corpus and
production readiness remain unverified.

### Operational discovery in the E02 review run

With the frontend's operational-pilot verifier, the E02 owner-chain fixture exercises Pipeline's existing delegated HTTP reads. Its test-only identity uses the real Pipeline JWT claim validator and a signed local token, a seeded VIEW grant, and the existing explicit GitHub facts double. Chromium discovers the incident from Operations and reloads the operational Workspace without a harness-supplied incident or diagnostic identifier. Look for `PILOT_PIPELINE_HTTP` and `PILOT_CHROMIUM`.

The existing review check remains a separate scoped diagnostic-ID path. PD-011/034 still need the authorized product association and query contract. No Pipeline or Recommendation production configuration is changed.

For the historical-provenance baseline, `PILOT_LEGACY_PROVENANCE` confirms the
PostgreSQL receipt retains both members after admitting evidence from two
capture revisions. The v1 receipt records only the latest contribution's scope
revision and each signal's evidence/context references. Repeat the E02
`pgvector-fixture` review command above to exercise this check along with
`PILOT_CHROMIUM` and the separate scoped P3 journey. A mixed or otherwise
unverifiable v1 receipt cannot certify the proposed incident-to-diagnosis read.
See the [proposed owner guarantees](https://github.com/pipesor-org/pipesor-docs/pull/23)
and [Ingestion restart evidence](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/pull/18).

### Synthetic aggregate capture receipt verification

Run from the Correlation checkout with the existing owner-chain prerequisites.
The peer defaults stage the local main commits and print their exact revisions.

```bash
PIPESOR_DEMO_SCENARIO=synthetic-aggregate \
PYTHON_BIN=/tmp/pipesor-demo-nlp/bin/python \
bash tools/verify_local_owner_chain.sh
```

The Ingestion PostgreSQL test creates a synthetic Maven failure, masks its
credential-shaped test value and exports the two frozen publication bodies from
the test build directory. The owner chain applies the actual snapshot to a
test registration, stores the protected evidence and sends NLP output to the
loopback Correlation endpoint. It verifies Recommendation receipt membership,
duplicate delivery and changed-source conflict. The broker handoff uses files
between test processes. The synthetic scope, identities and input bytes are
local controls. This scenario requires neither provider acquisition nor inference.

Set `PIPESOR_DEMO_REVIEW=1` and use the existing Chromium checker described above
to verify the aggregate locator in the diagnostic UI. This also reconstructs the
admitted diagnosis from PostgreSQL through the scoped P3 route and checks that
replaying the admission skips retrieval and inference.
