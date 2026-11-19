# Increment 4 · External evidence v1.0.0

## Source eligibility

| Priority | Source | Eligibility and disposition |
| --- | --- | --- |
| 1 | Zheng et al., *Why Do GitHub Actions Workflows Fail?* | The [replication package](https://github.com/zhengly1/workflow_failure) provides author annotations and links to jobs, and describes scripts used to extract original logs. Current unauthenticated GitHub log HEAD probes returned 403 for all ten previously selected jobs. Earlier probes returned 404. Neither response proves permanent absence. The repository license endpoint returned 404 and the package lists no license. Original log access and reuse permission remain unverified. All ten stay unexecuted. |
| 2 | Brandt, Panichella and Beller, *LogChunks* | The [Zenodo v1.0.0 release](https://doi.org/10.5281/zenodo.3632351) provides 797 Travis CI build logs and author-labeled failure passages. The release metadata lists [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The 24.1 MB archive passed the publisher MD5 and was pinned by SHA-256. Three cases also have an affirmative developer confirmation of the extracted passage in the release cross-validation CSV. This confirms the passage as useful failure evidence and leaves upstream cause unverified. Local use retains attribution and identifies the excerpt changes. |
| 3 | Moriconi et al., *GHALogs* | The [examined release](https://doi.org/10.5281/zenodo.10154920) publishes GitHub Actions logs and metadata under CC BY-SA 4.0, with run, job and step statuses. The release description supplies no independently labeled cause for a targeted case. It lists the logs as a single 142.3 GB archive. A targeted case would need an independently approved retrieval path and annotation. No archive or sample was downloaded from this release. |

Attribution for the included excerpts goes to Carolin Brandt, Annibale Panichella, and Moritz Beller, *LogChunks: A Data Set for Build Log Analysis*, v1.0.0, DOI 10.5281/zenodo.3632351. Rights for personal information and confidential material remain separate from the dataset license. The harness reads source material locally and only the reviewed excerpts enter NLP, Correlation, Recommendation and local RAG queries.

## Mapping and reference explanations fixed before execution

The [external manifest](../tools/preliminary/external-manifest.v1.json) contains the original log and annotation paths, build IDs, archive/log/XML/line/CSV digests and the reviewed changes. Source records remain Travis CI. The adapter uses the pinned protected GitHub-shaped envelope solely to test the local contract. Its account, installation, workflow, repository, tenant, scope, run, commit and timestamps are synthetic. Only the reviewed external log excerpt is copied into the NLP evidence, with a fixture timestamp and error prefix. All cases use the interpreted BUILD stage and unique synthetic message, run and correlation keys. No source workflow, commit or authorization relationship is inferred from those fixture values.

| Case | Independently annotated symptom | Data minimization and limitation |
| --- | --- | --- |
| E04 · Travis build 574602754 | Mix formatting check rejected unformatted files. | Remove ANSI and surrounding lines, including file list. The reason formatting drift occurred is unknown. |
| E05 · Travis build 566824752 | Compilation stopped on missing `libproc.h`. | Remove source path, row and column. The trace does not distinguish SDK availability from include settings or an incompatible host. |
| E06 · Travis build 549434310 | A timeout-wrapped build command exited 124. | Remove Travis timing telemetry, styling and other output. Keep the command as inert log text. The exit is consistent with timeout and does not explain the delay. |

The deterministic hypothesis surfaces the admitted failure line verbatim and explicitly withholds an upstream cause. It has no case-specific answer rules. Local PostgreSQL/pgvector holds one synthetic guide about collecting the first failing step and surrounding evidence. The reference explanations and annotations stay outside the corpus and inference input. This measures contract behavior, provenance and preservation of uncertainty rather than independent model diagnosis. Paid provider modes are unavailable to the external replay.

## Reproduce

From the Evidence Correlation repository with the dependencies in the [local guide](local-diagnostic-demo.md):

```bash
export PYTHON_BIN=/path/to/local-venv/bin/python
out="$(mktemp -d)"
curl -fLsS --max-filesize 30000000 \
  https://zenodo.org/records/3632351/files/LogChunks.zip -o "$out/LogChunks.zip"
for case in E04 E05 E06; do
  python3 -B tools/preliminary/external.py "$out/LogChunks.zip" "$case" --evidence "$out/$case"
  (cd "$out/$case" && sha256sum -c SHA256SUMS)
done
```

Each attempt retains the exact admitted inputs, owner-chain log, and checksummed receipt in a fresh directory. Failed attempts are evidence and should be retained. The original E01–E03 artifacts are outside this increment.

## Section 4 · Executed comparison

| Case | Deterministic outcome compared with the external annotation | Result |
| --- | --- | --- |
| E04 | Preserved the formatting-check failure. It agrees with the annotated symptom and leaves the reason for formatting drift open. | Accepted evidence citation and one scoped guide citation. |
| E05 | Preserved the unavailable header and excluded the source path. It agrees with the annotated compiler failure and leaves SDK, include path and host compatibility unresolved. | Accepted evidence citation and one scoped guide citation. |
| E06 | Preserved the exact exit status and wrapped-command context. It agrees with the annotated symptom and withholds a cause of delay. | Accepted evidence citation and one scoped guide citation. |

All three local deterministic owner-chain integration tests passed on 2026-11-19. For each case, the exact admitted excerpt survived NLP and correlation, Recommendation returned the verified diagnosis over authenticated HTTP, and the local pgvector probe found one authorized chunk, no alternate-scope matches, and HTTP 403 for a wrong caller. Existing fixture controls also exercised no-match, invalid citations and unsupported sources. The agreement above is symptom preservation by construction, so it provides no evidence of diagnostic accuracy or independent model reasoning.

**P1 partially supported.** The archive digest, log member and original line link to the excerpt, receipt and evidence citation. Reconstruction covers the admitted excerpt and replay, with source workflow context, upstream cause and surrounding log lines outside the observation.

**P2 partially supported.** The local synthetic scope controlled access to a separate generic guide. The dataset gives no trustworthy tenant or installation memberships with which to verify real authorization.

**P3 pending.** Human-review transitions were outside these runs. There is no basis here for measured accuracy, MTTR or TMD improvement, statistical significance, or generalization to other CI logs.

## Real-model candidates

Prioritize E05 and E06 after explicit approval of the exact minimized excerpts and a separate provider budget. E05 probes whether the model distinguishes the observed missing header from a conjectured SDK or host cause. E06 probes whether it preserves uncertainty about a timeout. E04 is an optional control for a clear formatting-gate failure. Keep all reference explanations out of retrieval and prompts. No Azure requests were made for Increment 4.

Original run directories and full checksums are under `~/.local/state/pipesor-external-increment4/E04`, `E05` and `E06`. The export includes the manifest, reviewed inputs, structured receipts and separate export checksums. Full original logs, XML, developer CSV, dataset archive and raw build output are excluded from the export.
