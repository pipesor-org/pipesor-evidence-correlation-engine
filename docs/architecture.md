# Architecture consumption

## Local implementation task — 2026-11-11

Scope: start the NLP → Correlation → Recommendation path without adopting an
unapproved receiver contract. Preserve execution isolation, evidence references
and `correlationId`. Excluded: GitHub access, broker consumption, diagnosis/RAG,
operational records/grants, foreign-table writes, delivery automation and cloud
changes. This section is the task plan and verification record; the dated
architecture summary below remains historical navigation.

### Revalidated sources and requirements

All checkouts were clean before work. `git ls-remote origin refs/heads/main`
matched the following local HEADs on 2026-11-11; no neighboring checkout was
modified. Correlation started on `main`, with HEAD and fetched `origin/main` both
`5508c6dafe4ada970a7af03a04383e81d87dba71`, no local diff. Work uses the new local
branch `feature/correlation-local-foundation`.

| Source | Integrated revision | Requirement / approval limit |
|---|---|---|
| [Docs](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain.yml) | `65792190d2ee32f0721d22848cd62cee1fd0d630` | ADR-015/018 Accepted; Java/Spring and backend conventions integrated. DD-014 and `correlated_contexts` confirmed; model status does not approve REST or delivery. No local overlay. |
| [Ingestion](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/blob/main/docs/deployment-contract.md) | `1066785908ff4a7f7f20b448df7154138ab093e0` | PR #15 integrated; owns capture/protection. Evidence v2 implemented; proposed Log/Event evidence identity is not approval by its owner. |
| [NLP](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/docs/integration-contracts.md) | `1d6e54f149abc493aec24a2cd43eab192d620373` | PR #17 integrated; consumes Ingestion v2. Its Correlation v2 handoff remains explicitly candidate. |
| [Pipeline](https://github.com/pipesor-org/pipesor-pipeline-service/blob/main/docs/architecture.md) | `84f7ac08eb4785c7ceaea2eb5761e43ca891b837` | Owns operational identities and authorization. Existing deterministic IDs do not prove existence or access. No agreed Correlation reference-validation API. |
| [Log/Event](https://github.com/pipesor-org/pipesor-log-event-service/blob/main/README.md) | `d1f027756a4fa36b2817dc42e7e0dec5aa8f9a09` | Documentation only; owns `evidence_items`; no executable reference-validation contract. |
| [Recommendation](https://github.com/pipesor-org/pipesor-recommendation-service/blob/main/README.md) | `754f9c64e69f764ae0eb5ad8000d2b53a1cd3fd2` | Documentation only; owns synchronous diagnostic orchestration. No accepted receiving URI/DTO. |
| [Organization contribution policy](https://github.com/pipesor-org/.github/blob/main/CONTRIBUTING.md) and [PR template](https://github.com/pipesor-org/.github/blob/main/.github/PULL_REQUEST_TEMPLATE.md) | `36691787bb9d0c9fd6b78c76bfe82adffa0c1c87` | Current remote main checked through GitHub API. Small feature branch, reviewed PR policy, no direct push; this task authorizes local work only. |

### Plan, affected surfaces and acceptance

1. **Analysis completed:** actual repository configuration exposes the focused
   `correlation_reviewer`; `explorer` performed read-only contract analysis.
   Candidate semantics and missing physical support are recorded in the
   [contract proposal](deployment-contract.md#local-contract-proposal-for-decision).
2. **Independent implementation:** worker owns Maven/wrapper, non-web Spring
   bootstrap/configuration, exact owned-table migration, infrastructure JPA
   mapping and isolated persistence tests. The coordinator owns existing docs
   and integration. Do not expose a repository as an authorized business use case.
   There are no local callers to migrate. The implemented upstream caller is NLP
   `ProcessEvidence` → `CorrelationHttpDelivery`; this increment does not change it.
3. **Verification:** run wrapper build, PostgreSQL migration/mapping/transaction
   checks and focused independent reviewer; record actual results in README.
   Resolve material findings before handoff. No production or live-service tests.
4. **Dependent implementation:** input resource/transformer, authorization,
   reference validation, domain/application correlation command, durable receipt
   and synchronous Recommendation adapter await the decisions below. Do not add
   empty ports, permissive adapters, in-memory receipts or speculative classes.

Observable acceptance for this independent slice: the build runs on Java 25;
Flyway creates only the modeled owned table and its technical history;
Hibernate validates it; UUID evidence arrays, incident/execution IDs,
`correlation_id` and microsecond UTC `created_at` round-trip; distinct executions
with identical summary/correlation coexist; duplicate PK insertion fails without
overwriting; failed transactions roll back; required columns reject nulls with
specific PostgreSQL causes. No REST listener or success response is provided.

These checks do **not** prove tenant/reference isolation, input sanitization,
correlation correctness or downstream receipt. Those are material acceptance
criteria for the blocked business slice, alongside auth/scope rejection,
message conflicts/concurrency, lost responses, unavailable references and failed
handoff. DD-014 stores provenance through logical evidence IDs, not full NLP
payloads. Business timestamps with nanoseconds must not be put into `created_at`
or silently rounded as a substitute for their future preservation contract.

No domain aggregate is introduced merely because a table exists. The JPA mapping
is an infrastructure representation, not a safe input boundary or sanitizer.
Only synthetic metadata is used in persistence checks; free-text summaries need
agreed minimization before an application write path can be enabled.

### Verification progress

Independent implementation completed: exact migration and internal immutable JPA
mapping, no business API or application command. Worker ran `./mvnw verify`
successfully; five PostgreSQL 17.11 checks passed without skips. It reran the suite
after adding assertions that Spring has no auto-configured Flyway bean and that
Hibernate remains in validation mode; all five passed again. The effective
Boot-managed set was inspected: Hibernate 7.4.13.Final, Flyway 12.4.0, PostgreSQL
JDBC 42.7.14, JUnit 6.0.3 and Testcontainers 2.0.5. An isolated JAR invocation with
empty datasource settings exited 1 as expected; no live connection was attempted.
The coordinator checked the resulting Surefire report, `git diff --check` and 19
local documentation links/anchors. See [README](../README.md#development-and-verification)
for repeatable commands and limits. Business reception, sanitization, reference
validation, durable idempotency and Recommendation delivery remain blocked only
for their dependent slice; no contract proposal is adopted by these tests.
The live `correlation_reviewer` completed its independent read-only review of
build, mapping, migration, tests and documentation with no material findings;
it inspected the Surefire evidence and ran `git diff --check`, without repeating
the suite or claiming live-service integration.

Consulted on 2026-11-06: `pipesor-org/pipesor-docs` full Git SHA **`c84ef3e4629fcb0c38315f32b641a5fb949e450d`**, using the clean local checkout (no uncommitted source overlay). Canonical model: `ey_aiops_ci_cd_diagnosis`, version `0.5.0-mvp-iac-alignment`, status `draft`. `evidence_correlation_engine` is a confirmed container with an independent repository under ADR-018; confirmed is a model status only.

Architecture source links below follow `main`. The summary reflects the consulted revision recorded above; review it against subsequent accepted decisions before implementation.

See the [documentation baseline](documentation-baseline.md) for the remote reconciliation and adoption limits of this update. Source SHAs are evidence identifiers, not frozen navigation links.

## Sources by subject

- [Source instructions](https://github.com/pipesor-org/pipesor-docs/blob/main/AGENTS.md), [architecture model](https://github.com/pipesor-org/pipesor-docs/blob/main/model/architecture.yml): `elements[evidence_correlation_engine]`, `relationships`, `deployment.container_hosts.evidence_correlation_engine`, `architectural_invariants` (INV-001/002/003/005/006/007/008/009/010), CTRL-001–006, `audit_trail`, `retention`.
- [Persistent model](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain.yml), [domain decisions](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md): table ownership, assumptions, retention and open questions. Domain owners are listed in the delivery contract; no derived SQL/DBML is consumed as independent authority.
- Applicable ADRs: [ADR-001](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-001-azure-container-apps.md), [ADR-002](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-002-azure-observability.md), [ADR-013](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-013-retention-365-days.md), [ADR-018](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-018-application-architecture-ddd-cqrs.md), [ADR-015](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-015-diagnostic-runtime-boundaries-and-orchestration.md). [ADR-019](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-019-align-mvp-with-iac.md) supersedes the affected historical APIM/Blob/foundation statements. For current decisions, read the [decision index](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/index.md) for subsequent supersession.
- [Proposed design](https://github.com/pipesor-org/pipesor-docs/blob/main/model/design.yml), [design rationale](https://github.com/pipesor-org/pipesor-docs/blob/main/model/design_decisions.md): retain proposed states and DCQ questions; not approved classes or API contracts.
- [Source provenance](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/sources/README.md), [reconciliation](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/reconciliation.md), [committed pending register](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md). Charter controls scope; architecture/ADRs control boundaries; drift/applicable technology ADRs control technology. Functional research is derived and its API v0.1 is proposed.

## Current MVP alignment

[ADR-019](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-019-align-mvp-with-iac.md) incorporates the accepted IaC direction from `pipesor-iac@4b42c0c8e66578dfb890ae1cca8c9c31c774026e`: [native ingress](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/adr-003-mvp-native-ingress.md), [resource baseline](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/mvp-resource-baseline.md) and [dev preparation](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/adr-004-mvp-state-and-deployment.md).

- Use ACA native ingress for authorized dashboard/workflow calls. APIM and replacement gateways are excluded from the MVP. Internal services remain internal; Entra authentication, service authorization, scopes and trace/audit propagation still require implementation and contracts.
- Exclude workload Blob. GitHub Actions remains the original log source; PostgreSQL holds sanitized evidence, metadata, excerpts and knowledge. Preserve provenance and generic references without inventing replacement locators or duplicating complete logs. Independent recovery and 365-day retention remain unresolved (PD-017/030/031). Terraform state Blob is separate.
- Use the Microsoft Foundry AIServices account/project foundation with public HTTPS and Entra-only authentication. Preserve the logical inference capability and PostgreSQL/pgvector ownership. Application endpoint/SDK, models, embeddings/dimension, geography, grants and corpus remain pending (PD-005/006/023/026/027).
- Terraform retains four isolated environment roots/states; only `dev` is selected for the temporary academic MVP. Services must be implemented, integrated locally and validated before separately authorized Azure actions. IaC preparation is not deployment evidence; future promotion rules remain applicable outside the dev exception.

PD-033–036 and the stage-5 report are committed at the consulted revision. The old uncommitted overlay description is superseded. The pending register identifies Jhon Galvez and Ramiro Guzman as contract approval authority; approval count, GitHub enforcement and actual delivery-contract approvals remain unspecified.

## Repository boundary inventory

ADR-018 specifies multirepo per deployable microservice. Repository names below apply the existing `pipesor-` prefix and kebab-case to canonical IDs; IDs are unchanged.

| Canonical container | Independent backend repository |
|---|---|
| `ingestion_normalization_service` | `pipesor-ingestion-normalization-service` |
| `pipeline_service` | `pipesor-pipeline-service` |
| `log_event_service` | `pipesor-log-event-service` |
| `evidence_correlation_engine` | `pipesor-evidence-correlation-engine` |
| `recommendation_service` | `pipesor-recommendation-service` |
| `nlp_log_processor` | `pipesor-nlp-log-processor` |
| `rag_retrieval` | `pipesor-rag-retrieval` |

`web_dashboard` already has `pipesor-frontend`. Managed Service Bus, PostgreSQL/pgvector, Entra ID and Microsoft Foundry are not custom backend service repositories. IaC already has `pipesor-iac`. `pretrained_llm_diagnostic_service` is an external managed service despite its ID (ADR-015). The diagnostic API, orchestrator, output composer and human review are internal to `recommendation_service` (ADR-016/017). Audit is cross-cutting (ADR-011); reporting was removed (ADR-009), detector/LogBERT are outside MVP (ADR-010), notifications are excluded (ADR-012), and direct scanner integrations are outside MVP (ADR-014). No repository is created for those capabilities.

No ambiguity blocks the seven service boundaries. Operational gaps remain: embedding access paths are not separately declared for NLP/RAG despite the documented provider, and the owners/mechanisms for configuration, migrations and promotion are incomplete. Those gaps do not authorize new relationships or services.

## Service relationships

Read these canonical IDs in `model/architecture.yml`; internal C4 projections do not add deployment boundaries:

- `rel_nlp_correlation`: `nlp_log_processor` → `evidence_correlation_engine` (REST/HTTPS).
- `rel_correlation_postgresql`: `evidence_correlation_engine` → `postgresql_operational_store` (PostgreSQL).
- `rel_correlation_recommendation`: `evidence_correlation_engine` → `recommendation_service` (REST/HTTPS).
- `rel_correlation_diagnostic_api`: `evidence_correlation_engine` → `diagnostic_api_component` (REST/HTTPS).

## Organization conventions consulted

Shared policy checkout/remote `main`: `pipesor-org/.github@36691787bb9d0c9fd6b78c76bfe82adffa0c1c87`; [contribution policy](https://github.com/pipesor-org/.github/blob/main/CONTRIBUTING.md), [security policy](https://github.com/pipesor-org/.github/blob/main/SECURITY.md), [PR template](https://github.com/pipesor-org/.github/blob/main/.github/PULL_REQUEST_TEMPLATE.md). Reuse organization defaults by reference, without duplicated community files or an unapproved license. Contribution rules remain owned by that policy; the baseline audit verified that the service repository is private, uses `main` and permits squash merges only. Existing Bun/Lefthook tooling belongs to those repositories; no application/toolchain dependency is introduced here.

Missing source approvals/provenance are tracked in PD-012–024, including individual ADR approval records (PD-014); preserve Accepted status while reporting those limits. Unresolved decisions relevant to this service: [PD-035](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-032](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-027](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-010](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-031](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-034](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md). Scope them to the affected integration or deployment rather than blocking repository initialization.

## GitHub enforcement limitation

The baseline audit confirmed private visibility, `main` as default branch and squash-only merge settings. The GitHub rulesets API returned HTTP 403 with a plan-upgrade requirement for this service; no enforced branch protection or independent approval gate is certified. PR-title/body squash formatting and automatic branch deletion were historical initialization settings, not independently revalidated by this audit. Mandatory reviewed PRs and no direct pushes to main remain contribution rules; server-side enforcement has not been established. Keep these repositories private; do not change visibility to work around that limitation. No empty CI checks or deployment workflows are added.
