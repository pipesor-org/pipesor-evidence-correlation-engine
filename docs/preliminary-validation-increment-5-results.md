# Increment 5 · External managed AI observations v1.0.0

Executed 2026-11-19 (America/Lima). The two exact reviewed [LogChunks](https://doi.org/10.5281/zenodo.3632351) excerpts and original mappings reverified against the Increment 4 bundle. See the [preflight](preliminary-validation-increment-5.md) for attribution, changes and record-level developer confirmation of each failure passage. The confirmations establish that the extracted symptom is relevant. Underlying causes remain unresolved.

## Observations

| Scenario | Actual managed result and comparison | Qualitative finding |
| --- | --- | --- |
| E05 · Missing `libproc.h` | The deterministic result preserved the compiler error and withheld an upstream cause. GPT-4o identified the same missing header and framed library absence and incorrect include paths as possibilities. | Symptom aligned and cause uncertain. The instruction to ensure the “prerequisite library” is installed is more specific than the excerpt and generic triage guide establish. The infrastructure explanation remains a hypothesis. |
| E06 · Exit status 124 | The deterministic result preserved the exact wrapped command and status. GPT-4o described a likely timeout, mentioned processing demand as an example to investigate, and said environmental and surrounding diagnostics were insufficient to confirm a cause. | Symptom aligned and uncertainty preserved. The delay's cause is unverified. Processing demand remains a troubleshooting suggestion. |

Both model requests returned HTTP 200 with a normal stop, and both diagnoses passed Recommendation's evidence and citation contract and the authenticated HTTP workflow. Each used one actual evidence citation and one authorized guide citation. Both returned no knowledge for the alternate scope and HTTP 403 for an unauthorized caller. Citation validity establishes origin and authorization for the supplied sources. It does not independently establish that every recommendation follows from the cited guide.

P1 is partially supported by reconstruction from source member and line through the minimized excerpt, execution and citation. P2 is partially supported by real-vector retrieval and the local scope probes. Original Travis CI tenancy and Entra scope were unavailable. P3 remains pending. These two observations support local feasibility only.

## Budget and transport

| Scenario | GPT-4o input / output tokens | Embedding attempts / input tokens | Estimated USD |
| --- | ---: | ---: | ---: |
| E05 | 354 / 147 | 5 / 70 | 0.002592040 |
| E06 | 360 / 135 | 5 / 82 | 0.002476804 |
| Total | 714 / 282 | 10 / 152 | 0.005068844 |

There were two outbound chat attempts, exactly one per case, and no retries. The maximum reservation was USD 0.067764224 against an approved USD 0.07 cap. Costs use the previously checked West US 3 retail rates and are estimates. The returned chat model was GPT-4o `2024-11-20`. All embedding calls used `text-embedding-3-small` version 1 and 1536 dimensions against fresh disposable PostgreSQL/pgvector environments.

The external transport admits exact reviewed text and the permitted guide, strips fixture framing and interpreted stage before chat transport, and strips fixture framing from embedding inputs. Provider chat requests use opaque citation aliases from the existing contract. Original platform identifiers, synthetic GitHub-shaped account and workflow fields, reference explanations, and raw logs remain local. Limits are enforced by starting once in each of the two protected case directories and reserving pre-priced, nonreusable attempt slots before sending. Maximum embedding input is 512 UTF-8 bytes, maximum chat payload is 8,000 bytes and maximum completion is 1,024 tokens. Budget calculations reserve up to 8,192 input tokens per chat including framing.

## Reproduction

Use the [offline verifier](preliminary-validation-increment-5.md#offline-verification) before staging. A repeat requires separate authorization for this paid experiment. In a single new mode-700 directory, run from Evidence Correlation with the existing Entra CLI session and the local dependencies configured:

```bash
export PYTHON_BIN=/path/to/venv/bin/python
umask 077
root="$(mktemp -d)"
archive="$HOME/.local/state/pipesor-external-increment4/LogChunks.zip"
bundle=../../artifacts/pipesor-external-evidence-v1.0.0.zip
python3 -B tools/preliminary/verify_external_foundry_inputs.py "$bundle" "$archive"
python3 -B tools/preliminary/external.py "$archive" E05 --evidence "$root/E05" --managed-bundle "$bundle"
python3 -B tools/preliminary/external.py "$archive" E06 --evidence "$root/E06" --managed-bundle "$bundle"
```

Stop on an authorization or provider availability failure. The versioned export retains observed model wording, citation decisions, deterministic comparison, usage and provenance hashes. Full logs and raw HTTP payloads are excluded. Extract to a clean directory and run `sha256sum -c SHA256SUMS` to verify export integrity.
