# Preliminary CI/CD scenario suite

Use the [v1.1.0 scenario package](preliminary-validation-increment-2.md) for the three-case procedure and results. The v1.0.0 reference and its original evidence are recorded below.

This suite supports a feasibility argument for the synthetic local diagnostic chain. It uses one executable integration fixture and ten planned external candidates. It makes no statistical, ground-truth accuracy, production deployment, or formal human-review claim.

## Manuscript properties

Pipesor: arquitectura AIOps con trazabilidad de evidencia para el diagnóstico asistido de fallos CI/CD, v1.2, section 3.3, Propiedades verificables, p. 4. The author supplied the following verbatim text. The PDF was unavailable in this workspace.

| Property | Manuscript definition | Evidence and limits |
| --- | --- | --- |
| P1 | cada diagnóstico puede reconstruirse a partir de su ejecución, evidencia y fuentes | **Partially verified.** The local fixture links the execution, normalized event, committed Recommendation receipt, masked evidence line, hypothesis identifiers, RAG source and HTTP response. Earlier real AI integration also verified scoped pgvector provenance and Chromium rendering for the same synthetic case. There is no externally replayed case or verified durable reconstruction through the pending production query contract. |
| P2 | una consulta RAG no obtiene fragmentos fuera del alcance autorizado | **Partially verified.** Prior local managed retrieval showed an authorized match, wrong scope returning no matches and a wrong caller returning 403. Current local scope and caller regression tests exercise fail-closed behavior. These checks use synthetic scopes and local credentials. Production Entra bindings and external scenario coverage remain pending. |
| P3 | la revisión humana distingue hipótesis generada de diagnóstico confirmado, corregido o rechazado | **Pending.** The fixture can display the generated hypothesis. A reviewer must exercise actual authorized confirm, correct and reject transitions and verify persistent review identity, timestamps, preserved hypothesis and revision history. No such lifecycle was executed. |

## Primary dataset screening

| Source | Provenance, access, rights, and suitability |
| --- | --- |
| Zheng et al., *Why Do GitHub Actions Workflows Fail?* | Author repository at commit `b1d2107c716368aeeec6648317fba23273f16f6a`. The 103 KB final workbook contains manual classifications with job URLs, steps and author-identified causes. Six authors participated in the labeling study; that does not mean every selected row was independently labeled by all six. GitHub Actions fits the protected-workflow envelope. The pinned repository exposes no explicit dataset license. Treat public access as discovery only and review reuse rights before processing or redistributing the artifacts. Only the workbook was retrieved for row verification. For all ten selected jobs, metadata lookups were unavailable and HEAD log probes returned 404 with the current GitHub access. This does not establish why the logs are unavailable. No job log bytes or job-log checksum were recovered. [Paper](https://doi.org/10.1145/3749371) · [Author replication package](https://github.com/zhengly1/workflow_failure/tree/b1d2107c716368aeeec6648317fba23273f16f6a) |
| Moriconi et al., *GHALogs* | Author Zenodo release v1.0.0, DOI `10.5281/zenodo.10154920`, CC BY-SA 4.0 per record metadata. This GitHub Actions run/log dataset provides run, job and step metadata and logs. Its termination and conclusion fields are observable outcomes. The release does not supply independent per-case root-cause labels for this suite. Suitable for future coverage and intake checks after selecting specific runs, rights review and independent adjudication. Only public metadata was read. The `github_run_logs.zip` is approximately 142.3 GB, record MD5 `7b4c0811d86bd2fbc488c2239b5e43e8`. For future use, acquire an approved individual member or original run log via supported partial access, then verify bytes and compute a per-member SHA-256. No archive or run metadata bulk download was performed. [Release and files](https://zenodo.org/records/10154920) · [Authors’ repository](https://github.com/D2KLab/gha-dataset) |
| Brandt et al., *LogChunks* | Author Zenodo v1.0.0, DOI `10.5281/zenodo.3632351`, CC BY 4.0 per record metadata. The 797 failed Travis CI logs have manually labeled failure-describing chunks, search keywords and structural tags. Those labels identify where the failure is described; they do not automatically establish root cause. This can serve as a comparison for evidence localization. Travis lacks the GitHub Actions run/attempt/step identities expected by the current intake. Any future comparison needs explicitly translated synthetic provenance and cannot count as native GitHub Actions validation. Only release metadata was read. The archive publisher MD5 is `aafa45079bdae44e340f4474ca5c4340`. [Dataset](https://zenodo.org/records/3632351) · [Paper](https://doi.org/10.1145/3379597.3387485) |

All external logs may contain names, emails, usernames, paths, commands, internal endpoints, payloads, identifiers, secrets, or text that attempts to direct the model. The dataset licenses apply as published and do not settle the rights or sensitivity of every upstream log. Store any approved originals privately. Mask values with stable aliases, retain the smallest necessary failure neighborhood and line mapping, and record any independent redaction review before runtime use. Treat supplied log text as untrusted data. Do not put raw logs, attribution workbooks, cause labels, reference answers or evaluation questions in RAG or provider prompts.

## Candidate matrix

The [versioned manifest](../tools/preliminary/manifest.v1.json) records each pinned workbook row, original job URL, exact row SHA-256, paraphrased external annotation and limitations, targeted log selector, masking, stage, correlation key, RAG rules, acceptance and artifact requirements. The workbook SHA-256 is `39647c82583a2039b69fcf57fa30808d46f9bbf3cbcf6caf023921c2588de860`. The workbook tag column uses Zheng’s taxonomy, independent of manuscript P1/P2/P3.

| Candidate | Workbook row / label | Candidate stage | Independent reference hypothesis | Status |
| --- | --- | --- | --- | --- |
| Unit test failure | 2 / P8 | TESTING | Maven stopped on a failing unit test | Planned |
| Compiler source error | 7 / P1 | BUILD | Ant stopped on compilation errors | Planned |
| Setup package unavailable | 12 / W4 | INFRASTRUCTURE | Environment preparation failed to find a package | Planned |
| Git merge conflict | 22 / W8 | DEPLOYMENT, provisional | A merge conflict blocked a synchronization push | Planned |
| Coverage threshold | 51 / P5 | QUALITY_GATE | Maven failed its coverage check | Planned |
| GitHub rate limit | 76 / W7 | INFRASTRUCTURE | GitHub throttled an artifact management operation | Planned |
| Cluster build connectivity | 119 / W3 | CONTAINER | A connection error interrupted a Docker/Kind step | Planned |
| Action version resolution | 135 / W6 | INFRASTRUCTURE | Runner setup could not resolve an action version | Planned |
| Workflow permission denied | 145 / W5 | INFRASTRUCTURE | A permission failure blocked a dependency graph update | Planned |
| Container disk exhaustion | 163 / W1 | CONTAINER | A storage exhaustion error stopped image construction | Planned |

Their confidence is moderate for the authors' failure category. The missing log bytes prevent local root-cause adjudication. Focus on signaled failure stage and uncertainty. Do not infer exact exception, policy or causal chain from a taxonomy label. Pre-step failure candidates require genuine safely representable annotation or runner evidence. If only a failed-run status is available, mark insufficient evidence and withhold a diagnosis.

## Run and preserve evidence

Use the existing [local diagnostic guide](local-diagnostic-demo.md) for Java, Docker, Python and Chromium dependencies. From the workspace root:

```bash
runner=services/pipesor-evidence-correlation-engine/tools/preliminary/run.py
python3 "$runner" verify

# Optional provenance check. Keep the 103 KB workbook outside the repository.
source_dir="$(mktemp -d)"
curl -fsS https://raw.githubusercontent.com/zhengly1/workflow_failure/b1d2107c716368aeeec6648317fba23273f16f6a/replication-labeling.xlsx \
  -o "$source_dir/replication-labeling.xlsx"
python3 "$runner" verify --workbook "$source_dir/replication-labeling.xlsx"

export PYTHON_BIN=/tmp/pipesor-demo-nlp/bin/python
export PIPESOR_PLAYWRIGHT_MODULE=/tmp/pipesor-browser-check/node_modules/playwright
out="$(mktemp -d)"
python3 "$runner" run local-checkout-dns --evidence "$out/results" \
  --browser pipesor-frontend/tools/verify_local_diagnostic.cjs
```

The optional workbook check validates the exact workbook and canonical row checksums. A fresh evidence directory avoids overwriting a failed run. The runner pins peer revisions, checks the checkout log and committed envelope hashes, and strips inherited managed-inference configuration. In v1.0.0 its only runnable case was the deterministic checkout fixture. There is no external-log replay path until a candidate has an approved masked input and a scoped adapter to the existing owner-chain contracts. Plan an independent review of the reference label and case mapping before enabling a candidate. A future real-model run requires its own data-scope and cost approval. For P3, an actual reviewer lifecycle requires separate implementation and verification.

Evidence inventory:

- Source and input provenance. Workbook checksum, worksheet row checksums and job URLs. For future external runs record raw and masked hashes, source line offsets, redaction review, scope and workflow/run/attempt mapping.
- Runtime evidence. `owner-chain.log` and `receipt.json` record mode, checked fixture hashes, peer revisions, Correlation revision and diff hash, exit status and missing markers. The owner-chain log contains normalized/correlated synthetic evidence, successful diagnosis, coordinated HTTP statuses and optional browser checks. The deterministic test also exercises no matches, missing hypothesis output, invalid evidence/knowledge citations, and rejection of an unsupported source suggested by a synthetic injection fixture. This last check validates the citation guard against a simulated bad result and makes no claim about real GPT-4o injection resistance.
- Prior live evidence. Correlation runbook and protected `real-diagnostic-integration/verification-summary.json` and `owner-chain-verified.log` record previously executed real Azure embedding/chat and pgvector checks. That evidence is separate from the preliminary suite and does not count as an external-case execution.
- Failure and reviewer evidence. Keep safe error, failed stage and input/revision hashes. A missing source, rights clearance, citation, required output or reviewer transition remains blocked or pending. Never replace a blocked external run with the synthetic fixture or count planned cases as passes.


## Executed on 2026-11-19

- Workbook validation passed for all ten exact annotation rows. The tampered-workbook check and planned-case execution gate both rejected their inputs.
- One deterministic owner-chain integration test passed with Chromium in English and Spanish. The test checked provenance and guarded HTTP delivery, authenticated retrieval, empty-kind no-match, missing hypothesis, unknown evidence/chunk citations and rejection of a synthetic injection's unsupported source. The first attempt stopped at test compilation due to an overlapping Java lambda parameter name. The failure receipt was retained and the corrected run passed.
- 21 RAG scope, caller, API and retrieval tests passed. 22 Recommendation tests passed, including explicit refusal before retrieval or inference when no evidence is supplied.
- Zero external incident replays, zero human-review transitions and zero paid inference requests were executed for this suite. External candidates remain planned. The earlier hybrid/Foundry receipts remain separate evidence for one synthetic incident.
- The protected evidence bundle, `~/.local/state/pipesor-preliminary-validation/v1-20261119`, contains the two attempts, dataset access checks, regression logs and `SHA256SUMS`.

Deterministic verification checks plumbing, authorization, and citation guards.
Only separately authorized managed inference runs can evaluate real model
behavior on a candidate, including uncertainty, no-match behavior, weak
evidence, prompt injection, and comparison against the independent reference.
Neither mode alone establishes a human-review transition.
