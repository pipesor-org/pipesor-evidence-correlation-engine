# Registered E02 advisor run

> **Historical record.** The merged-classpath harness this page uses (`tools/local-handoff`, `tools/verify_local_*.sh`, `tools/preliminary/run.py` and `external.py`) was retired on 2026-11-22 when the services became real runtimes (ADR-022). Its results stay valid as dated evidence; reproduce them from commit `25ad9c7` of this repository. Scenario manifests and fixtures under `tools/preliminary/` are kept.

The existing Pipesor workflow form registers the E02 repository/workflow through Pipeline HTTP. The registered pipeline is read back and must match the unchanged E02 envelope before replay. No pipeline or repository row is seeded in this mode. The standalone registration pilot uses provider IDs `823901/918901`; this run derives its synthetic catalog and server-side GitHub facts from the original E02 envelope. The browser never supplies a permission flag. The test uses the existing signed delegated Pipeline identity and a separate P3 review credential.

## Interactive startup

Prerequisites are Java 25, Docker/Testcontainers, Python 3.14 with the NLP and RAG dependencies used in the [owner-chain guide](local-diagnostic-demo.md), Node and the frontend's frozen Bun dependencies. From the workspace root in terminal A:

```sh
export PIPESOR_DEMO_INTERACTIVE_DIR="$(mktemp -d /tmp/pipesor-advisor.XXXXXXXX)"
printf 'Session directory %s\n' "$PIPESOR_DEMO_INTERACTIVE_DIR"
export PYTHON_BIN=/path/to/venv/bin/python
export PIPESOR_DEMO_SCENARIO=E02 PIPESOR_DEMO_RAG_MODE=pgvector-fixture
export PIPESOR_DEMO_LLM_MODE=fixture PIPESOR_DEMO_REVIEW=1
unset PIPESOR_DEMO_REGISTRATION_BROWSER PIPESOR_DEMO_BROWSER_CHECK
bash services/pipesor-evidence-correlation-engine/tools/verify_local_owner_chain.sh
```

After `PIPESOR_ADVISOR_READY`, use the printed directory in terminal B, from the workspace root:

```sh
export PIPESOR_DEMO_INTERACTIVE_DIR=/tmp/pipesor-advisor.REPLACE_ME
export PIPESOR_DEMO_PORT="$(cat "$PIPESOR_DEMO_INTERACTIVE_DIR/port")"
export PIPESOR_DEMO_REGISTRATION_CATALOG="$(cat "$PIPESOR_DEMO_INTERACTIVE_DIR/catalog.json")"
cd pipesor-frontend
bun run dev --host 127.0.0.1 --port 5176 --strictPort
```

1. Open `http://127.0.0.1:5176/pipelines`. Enter the Pipeline pilot credential from the session's `pipeline-token` file privately. Pipelines starts empty.
2. Choose **Add monitored workflow → pilot/reporting-service → Integration checks**. Set criticality, leave monitoring **Active**, choose Platform Engineering and submit. The existing Pipeline Detail screen reads the persisted record. A reload prompts again for the Pipeline credential. Before replay there are zero incidents for the registered pipeline.
3. In terminal C run `touch /tmp/pipesor-advisor.REPLACE_ME/replay`. Watch terminal A for `PIPESOR_E02_BINDING` and `PIPESOR_ADVISOR_DIAGNOSIS_READY`. The fixture creates a failed execution and incident through Pipeline's snapshot handler. Protected synthetic evidence then flows through Log/Event, NLP, Correlation, RAG and Recommendation.
4. Open `http://127.0.0.1:5176/operations?queue=active&pilot=1`, use the Pipeline credential and load the incident. Its operational Workspace remains independent from the blocked general diagnosis lookup.
5. Append the private session file `review-path` to `http://127.0.0.1:5176` and load the admitted diagnosis using `review-token`. Check the unresolved dependency coordinate, limitations, evidence and scoped knowledge citation. Use the URL in `validation-path` with the same **P3** credential to record a review. Reload the Workspace P3 link, reauthenticate, and check the retained review. The harness passes the admitted diagnostic UUID only through this explicit P3 handoff.
6. Run `touch /tmp/pipesor-advisor.REPLACE_ME/stop` after recording a review. The harness checks persisted review history and completes its negative assertions. Stop Vite with Ctrl-C. The harness cleans up its credential files and disposable stores. Remove the empty session directory with `rmdir`. Each pause is bounded to ten minutes. Ctrl-C in terminal A stops an abandoned session.

Do not display the credential files during a recording. The normal application has neither the synthetic test composition nor these P3 endpoints.

## Automated reproduction

Set the same Python, E02, RAG, inference and review variables, with no interactive directory. Set `PIPESOR_PLAYWRIGHT_MODULE` only if Playwright is outside frontend dependencies.

```sh
unset PIPESOR_DEMO_INTERACTIVE_DIR
export PIPESOR_DEMO_REGISTRATION_BROWSER="$PWD/pipesor-frontend/tools/verify_workflow_registration.cjs"
export PIPESOR_DEMO_BROWSER_CHECK="$PWD/pipesor-frontend/tools/verify_local_review.cjs"
bash services/pipesor-evidence-correlation-engine/tools/verify_local_owner_chain.sh
```

The existing standalone `WorkflowRegistrationBrowserTest` in Pipeline verifies backend restart against retained PostgreSQL. Recommendation's `ReviewLifecycleTest` separately verifies admitted versions, immutable review history and restart. The interactive owner chain keeps its stores for the session and discards them afterward.

## Actual managed path and gate

`DemoDiagnosis` chooses the managed adapter before passing it into the same `PersistedDiagnosticWorkflow` used by P3. The original shell gate limited P3 to deterministic E02. In the approved-budget case, E02 now accepts `PIPESOR_DEMO_RAG_MODE=hybrid`, `PIPESOR_DEMO_LLM_MODE=foundry` and `PIPESOR_DEMO_FOUNDRY_REVIEW_APPROVED=1` with the existing [protected receipt directory and embedding settings](local-diagnostic-demo.md#opt-in-to-real-gpt-4o-diagnostics). Set that flag only for an expressly approved new run and use its approved ledger. Existing request bounds, citation validation and replay fingerprinting apply. The managed P3 combination still needs a funded execution to verify it as a whole.

Read-only Azure checks on 2026-11-19 acquired a Cognitive Services token through Azure CLI. Both deployments were in Succeeded state. Chat is `pipesor-dev-diagnostics`, GPT-4o `2024-11-20`, with a configured 2,000 TPM and 2 requests per 10 seconds. Embeddings is `pipesor-dev-embeddings`, `text-embedding-3-small` v1, 1,000 TPM and 1 request per minute. The checks do not establish remaining shared quota or inference data-plane access. No new inference budget was supplied for this task. Historical E02 and E03 receipts under `~/.local/state/pipesor-preliminary-validation/v1.2.0-foundry/` returned HTTP 200 from `gpt-4o-2024-11-20`. E02 used 387 prompt / 131 completion tokens and E03 used 298 / 127. Those historical results did not exercise registration and P3 together.

## Verified source baseline

The requested PRs are merged. This table records the commit inspected on each `main` before the bridge changes.

| Repository | Commit | Boundary and remaining gap |
| --- | --- | --- |
| Frontend | `94bc9dd` (#27; #25 at `0d5092c`) | Existing registration, inventory and detail API pilot, Operations incident discovery and scoped local P3 view. Catalog and signed identities are synthetic. |
| Pipeline | `6f9403d` (#13) | Real HTTP and PostgreSQL registration, policy and incident reads. The failed execution uses the real transformer and snapshot application handler, with no broker. Production connector remains unavailable. |
| Ingestion | `f243bbd` (#18) | Read and machine capture adapters exist. This run supplies a pre-protected, pinned E02 envelope and does not run Ingestion HTTP, a GitHub webhook or Service Bus. |
| Log/Event | `6f4dfa8` | Real evidence persistence and scoped membership HTTP in the harness. Registration is an explicit store call with synthetic protected input. |
| NLP | `92fea50` | Actual parser/processing code invoked by the fixture sender and signed Correlation HTTP handoff. No Service Bus receive. |
| Correlation | `02aaa28` (#22) | Real receiver, durable contexts and receipt delivery. Legacy multiple-member scope limits preserved. |
| RAG | `1772c18` | Real scoped HTTP, PostgreSQL/pgvector and synthetic corpus. The default run uses deterministic embeddings. |
| Recommendation | `fa62392` (#12) | Owned persisted admitted snapshots and P3 history. Independent test-only authorizer. Azure inference exists and requires the approved path above. |
| Docs | `9ae7c86` (#23) | ADR-020 ownership/current-access constraints remain in force. PD-011/034 approval does not supply missing historical generation or member provenance. |
| IaC | `36402dc` | Read only for current Foundry naming and resource context. Local test uses no IaC resources except an explicitly approved managed call. Existing untracked provisioning notes were left intact. |

E02 fixture SHA-256 remained `2ac4abc7dd7b04944a5f5d259340f28cf2d2fca2dc6f120004ac377a3ddfb34c` for JSON and `8be9ac6e31ebd35e379279ef4d62f42f81c56712456ff4364386955dd1b8ec82` for the log. The failed execution is synthetic. The operational and diagnostic reads are real within their distinct test scopes. See the separate [OE3 evidence inventory](registered-e02-oe3.md).

## Execution evidence from this increment

On 2026-11-19, Chromium drove the interactive handoff on port 5176. It submitted the Paper form (HTTP 201), signaled replay, located the matching incident in Operations, submitted a review (HTTP 200), and opened the admitted diagnosis and retained review through the separate scoped Workspace link. The Java suite then passed the remaining scope denial, mixed-revision provenance and duplicate evidence checks. Recommendation's separate retained-PostgreSQL restart test passed.

Local evidence for this run is `/tmp/pipesor-mvp-interactive.log`, `/tmp/pipesor-mvp-interactive-browser.log`, and the registration, Operations and P3 screenshots under `/tmp/pipesor-registered-e02-evidence/`. The interactive command above reproduces the state for a fresh screenshot. The baseline browser runner encountered interference while falling back from an occupied Vite default port. The two browser verifiers now use fresh loopback listeners and isolated Vite caches.
