# Increment 3 · Real Azure AI verification v1.2.0

This increment replays E02 and E03 from the [immutable v1.1.0 scenario manifest](../tools/preliminary/manifest.v1.1.json), SHA-256 `14f23d2c59c726bcf29867ec1eb2ecbb11145c7a363a88d76f3810a493a390eb`. The [deterministic results](preliminary-validation-increment-2.md) and their receipts remain a separate baseline.

## Method

Each case starts a disposable PostgreSQL 17/pgvector container using the existing image digest and applies the RAG migrations through `20261116_03`. The existing Azure embedding adapter generates both stored and query vectors through `pipesor-dev-embeddings`, `text-embedding-3-small` v1, at 1536 dimensions. The harness records response model, token count and dimensions for every successful embedding call. E02 seeds a generic dependency guide in the authorized scope. E03 seeds the same guide under a separate synthetic scope revision. The locked scenario inputs provide the CI evidence. The independent reference causes remain in the manifest, outside both the knowledge corpus and model prompt.

The owner chain processes the same log, normalization, correlation and caller checks as v1.1. Recommendation calls `pipesor-dev-diagnostics`, GPT-4o `2024-11-20`, with its existing structured-output adapter and citation validator. It tests the existing authenticated local diagnosis API. Both deployments use West US 3 US Data Zone Standard and the existing `services.ai.azure.com` origin. Foundry uses the local Azure CLI-backed Entra credential. Service caller and local browser-facing API authorization are fixture mechanisms.

The reviewed deployments were in `Succeeded` state and the Azure CLI obtained a fresh token for `https://ai.azure.com/.default` before execution. The shared private ledger provides six chat attempt slots including failed attempts. This increment makes at most one chat request per case, with no retries. Requests are capped at 8,000 JSON bytes and 1,024 completion tokens. Each disposable RAG process permits eight embedding calls of up to 512 input bytes without retries. Using 8,192 input tokens per chat as a conservative allowance for the byte-bounded request plus framing, the six-slot estimate plus both embedding budgets is below USD 0.203. Rates are USD 2.75/million chat input tokens, USD 11/million chat output tokens and USD 0.022/million embedding tokens, using West US 3 retail Data Zone meters. Estimates charge all input at full price. [Microsoft Azure pricing and Retail Prices API reference](https://learn.microsoft.com/en-us/azure/cognitive-services/openai/faq) · [Azure Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices)

## Reproduce

Use the [local diagnostic guide](local-diagnostic-demo.md) to prepare Java 25, Docker and the Python environment with both services' dependencies. Confirm current deployment versions, Foundry token availability, and a fresh request and cost authorization before repeating. Run from the workspace root:

```bash
runner=services/pipesor-evidence-correlation-engine/tools/preliminary/run.py
export PYTHON_BIN=/path/to/hybrid-venv/bin/python
umask 077
out="$(mktemp -d)"
mkdir -m 700 "$out/ledger"
python3 "$runner" verify
python3 "$runner" run E02 --foundry-ledger "$out/ledger" --evidence "$out/E02"
python3 "$runner" run E03 --foundry-ledger "$out/ledger" --evidence "$out/E03"
for case in E02 E03; do
  (cd "$out/$case" && sha256sum -c SHA256SUMS)
done
```

Use one ledger for the authorized increment and a fresh output directory for each case. Stop if access or provider availability fails. Preserve failed outcomes and attempt files. A citation rejection or a finding that differs from the reference remains an observed outcome. An HTTP 200 from Foundry alone does not establish a valid diagnosis. The private attempt files contain the actual synthetic output and provider metadata, without credentials, full prompts or raw provider responses.

## Executed outcomes

| Case | Real-model outcome | Comparison with the deterministic baseline |
| --- | --- | --- |
| E02 · Dependency resolution | GPT-4o suggested that the requested version might be missing from the offline dependency index. Recommendation accepted one log citation and one authorized guide citation. The 200 diagnosis preserved the exact unresolved coordinate in the evidence. RAG returned no match for the other scope and 403 for an unauthorized caller. | Both treatments identify the missing dependency version at the level supported by the log. The deterministic hypothesis names the coordinate directly. GPT-4o phrases it tentatively and recommends checking the index configuration. |
| E03 · Ambiguous build failure | GPT-4o said that the specific cause remained unclear because diagnostic details were missing. Recommendation accepted the exit-status citation and zero knowledge citations. The guide was retrievable in its own scope, while the incident scope received no match. An unauthorized caller received 403. | Both treatments preserve uncertainty and withhold a specific cause. The deterministic hypothesis asks for the failed command and stderr. GPT-4o suggests broader manual inspection and mentions dependencies and environment settings as possibilities to inspect. |

Each managed case passed one complete owner-chain integration test and produced a local authenticated HTTP 200 diagnosis after the 401 and 503 checks. Each GPT-4o request returned HTTP 200 and a normal stop. The tests also exercised invalid citations, unsupported sources suggested by injected text, and no-match handling with deterministic negative controls. The managed calls do not establish resistance to prompt injection.

| Case | Chat input tokens | Chat output tokens | Embedding calls / tokens | Estimated USD |
| --- | ---: | ---: | ---: | ---: |
| E02 | 387 | 131 | 5 / 120 | 0.002507890 |
| E03 | 298 | 127 | 5 / 104 | 0.002218788 |
| Total | 685 | 258 | 10 / 224 | 0.004726678 |

Two of six authorized GPT-4o slots were consumed. The estimate uses retail token rates and does not represent an Azure invoice. P1 is partially supported by the recorded inputs, execution identifiers, evidence, citation decisions and delivered diagnosis. P2 is partially supported by the real-embedding scope probes and the wrong-caller rejections with local test authorization. P3 remains pending. These two synthetic observations establish neither diagnostic accuracy nor comparative performance. Formal validation, production Entra authorization, and review transitions remain open.

Observed evidence is under `~/.local/state/pipesor-preliminary-validation/v1.2.0-foundry/`. The E02 and E03 directories each contain `owner-chain.log`, a structured `receipt.json`, the synthetic provider output in an `attempt-*.json` file, and `SHA256SUMS`. The root `summary.json` links their hashes, input and model metadata, token usage and cost. The original v1.0.0 and all three v1.1.0 receipts reverified against their existing checksums. The default-mode E03 regression passed without managed calls, and its receipt is in `E03-deterministic-regression/`. The managed runner rejected both an out-of-scope E01 invocation and a repeat E02 invocation against the same ledger before making a request.
