# Increment 5 · External Foundry preflight

This document records the pre-execution checks and approval boundary. See the [executed results](preliminary-validation-increment-5-results.md) for observed model outputs, usage, and P1/P2 findings. Read-only Azure metadata showed both named deployments in Succeeded state with the expected versions and Data Zone SKU. The three West US 3 retail meters were independently checked.

Brandt, Panichella and Beller published [LogChunks v1.0.0](https://doi.org/10.5281/zenodo.3632351) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The license permits reuse and adaptation with attribution and disclosure of changes. The review applies to the two minimized excerpts below. Raw dataset material remains local.

| Case | Original Travis CI build and annotated source | Direct developer confirmation | Reviewed error text and reference boundary |
| --- | --- | --- | --- |
| E05 | 566824752, line 930 | Cross-validation CSV row 27, extraction valid, no override | `fatal error: 'libproc.h' file not found`. Removed source path and compiler location. The symptom leaves SDK, include path and host configuration unresolved. |
| E06 | 549434310, line 3150 | Cross-validation CSV row 148, extraction valid, no override | `The command "$TIMEOUT 35m ci/build.sh" exited with 124.` Removed Travis telemetry and terminal formatting. The command is inert text. The exit is consistent with a timeout and leaves the reason for the delay unresolved. |

The exact excerpts contain no credentials, personal information or sensitive identifiers in the material reviewed. The CSV confirms passage extraction individually for each build. The XML annotation and CSV are kept outside both the RAG corpus and the chat request. The [source manifest](../tools/preliminary/external-manifest.v1.json) distinguishes the original Travis records from the synthetic GitHub-shaped envelope. The synthetic timestamp, account, repository, workflow, run and commit fields stay local. The model receives the evidence alias, interpreted BUILD stage and reviewed error text, plus one scoped synthetic guide: “Preserve the first failing step and collect surrounding diagnostic output before assigning a cause.” The fixed timestamp and error prefix in the normalized evidence are fixture framing. Embedding scope probes use the harness’s generic probe text. Raw logs, original IDs, annotation, answers and full envelopes are excluded from provider inputs.

## Offline verification

From the Evidence Correlation repository:

```bash
python3 -B tools/preliminary/verify_external_foundry_inputs.py \
  ../../artifacts/pipesor-external-evidence-v1.0.0.zip \
  "$HOME/.local/state/pipesor-external-increment4/LogChunks.zip"
```

This verifies the pinned archive and manifest, all 11 exported checksums, original member/line and XML/CSV checksums, the record-level confirmation, and byte equality with recreated replay inputs.

## Approval boundary and run design

- Authorize only E05 and E06 against the existing `pipesor-dev-embeddings` and `pipesor-dev-diagnostics` deployments. Require the same models as Increment 3, `text-embedding-3-small` v1 at 1536 dimensions and GPT-4o `2024-11-20`, US Data Zone Standard, Entra authentication. Refuse a changed model, route, input or guide.
- Keep a separate disposable PostgreSQL/pgvector database per case and the existing production-shaped local NLP, Correlation, RAG and Recommendation contracts. Use the existing citation validator and test authenticated local HTTP delivery. Check authorized retrieval, alternate-scope no-match and wrong-caller HTTP 403. Store corpus and query vectors from the same managed embedding deployment.
- Limit the run to two outbound chat attempts total, one per case, with no retries, and at most eight embedding attempts per case with no retries. Reuse the existing 8,000-byte chat request and 1,024-token completion limits and 512-byte embedding input limit. Reserve attempts before sending; count errors and timeouts against the limit. At 8,192 estimated input tokens per chat and 512 per embedding, use the retail rates USD 2.75, 11 and 0.022 per million chat input, chat output and embedding tokens. Estimated combined ceiling is USD 0.067764224. Request an approval cap of USD 0.07. Recheck deployment identity and rates if they change. Stop on authorization or provider availability failure. [Azure retail pricing API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices)
- The default external replay is deterministic. The managed opt-in requires the pinned evidence bundle and the preallocated attempt slots for the reviewed cases. Run the offline verifier before the first outbound request.
- Retain the bounded structured model output privately for review. In the academic evidence export, retain observed model wording after review, citation IDs, validation reasons, scope results, HTTP statuses, provider model/version, token usage, actual retail-cost estimate, and SHA-256 for staged inputs, receipts and run output. Keep transport/contract pass separate from qualitative judgment. Preserve failure receipts without retrying to improve the result.

E05 is qualitatively aligned if the diagnosis identifies the missing header and treats SDK or environment explanations as hypotheses. A definite upstream cause is overconfident. E06 is aligned if the diagnosis reports status 124 as consistent with timeout and withholds a definite explanation for the delay. Invented causes or citations remain observed failures. P1 and P2 can receive scoped supporting evidence only after execution. P3 remains pending human review. No statistical accuracy conclusion is planned.
