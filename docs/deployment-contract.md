# Deployment contract: evidence_correlation_engine

**Status: readiness record; not approved for IaC consumption or deployment.** Prepared for PD-035. [Architecture consumption](architecture.md) records the consulted source revision `c84ef3e4629fcb0c38315f32b641a5fb949e450d`, model IDs, applicable ADRs and committed PD-033–036. ADR-019 governs the current MVP alignment.

**Documented fact** means recorded in those sources/policies, not proven implementation or independent approval evidence. **Proposal** requires acceptance; **Unknown** remains pending. The 2026-11-11 local increment introduces only build/bootstrap and the modeled owned-table persistence foundation; see [task scope](architecture.md#local-implementation-task--2026-11-11) and [verification](../README.md#development-and-verification). No receiving endpoint, image delivery, credentials or cloud resources are implemented. IaC contains infrastructure preparation; provisioning is not evidenced here.

## Identity and runtime

| Requirement | Value | Source and status |
|---|---|---|
| Identity and responsibility | `evidence_correlation_engine` — Correlate protected evidence across workflow execution context, preserve provenance and deliver correlated incident context for diagnosis. | Documented fact: `elements[evidence_correlation_engine]`, ADR-018. |
| Independent deployment | Yes; separate backend repository/container on Microsoft Azure Container Apps. | Documented fact: `deployment.container_hosts.evidence_correlation_engine`, ADR-001/015/018 as applicable. No deployed service resource is evidenced by this repository. |
| Language/framework | Java / Spring Boot. | Documented fact: element `backend`, ADR-018. See the session-authorized development baseline below. Dependency pins and base-image selection remain implementation/delivery requirements under PD-032/035. |

## Development baseline

User direction recorded on 2026-11-06 retains the installed Java 25.0.4.1, Python 3.14.8, Docker 29.9.3 and Git 2.55.0. Java services use Maven (installed: 3.10.0); Azure CLI 2.92.0 is available for separately authorized operations. Select the latest stable dependency versions compatible with that baseline, and pin them when application build/dependency files are introduced. Framework and transitive dependency compatibility must be checked together; availability on this workstation is not container/CI verification. Python dependencies belong in isolated project environments.

This session direction supplements the consulted architecture revision; it does not close PD-032's identity/hash choices or PD-035's base-image and delivery requirements.

The local foundation selects Spring Boot **4.1.2**, the latest stable version in
Maven Central checked on 2026-11-11 (4.2.0-RC2 is a preview). Its
[system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
support Java 25 and Maven 3.10.0. Use its dependency management for the compatible
Hibernate, Flyway, PostgreSQL driver, JUnit and Testcontainers versions; the parent
pins that dependency set. Maven Wrapper 3.3.4 pins Maven 3.10.0. These are local
build choices, not a container baseline or deployment approval. Runtime is non-web
and Flyway is disabled by default; isolated tests explicitly enable migrations.
There is no public/internal business listener, auth configuration or permissive
adapter. Enabling the receiver requires the decisions in the
[contract proposal](#local-contract-proposal-for-decision).

## Integrated infrastructure baseline

The [IaC resource baseline](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/mvp-resource-baseline.md) at `pipesor-iac@4b42c0c8e66578dfb890ae1cca8c9c31c774026e` selects GHCR and system-assigned identity per future Container App. Identity is created with its app; it is not an existing identity or a scoped access grant. The [native-ingress decision](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/adr-003-mvp-native-ingress.md) preserves service-side authorization, and [ADR 004](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/adr-004-mvp-state-and-deployment.md) restricts current preparation to dev with separately authorized activation and Azure actions.

Shared opt-in Terraform foundations include the external-capable ACA environment/VNet, Entra-only public Service Bus Standard namespace, private Entra-only PostgreSQL 17 Flexible Server (B2s, 32 GiB, seven-day local backups, no HA), shared Log Analytics/Application Insights and public Entra-only Foundry account/project. These are approved IaC preparation choices with checked-in infrastructure code, not evidence of deployed or reachable dependencies. They do not add a database, broker or AI relationship to a service that lacks one in the architecture model.

PostgreSQL's `vector` allowlist setting does not execute SQL extension creation, schemas, migrations or grants. Broker entities/delivery semantics, SQL/token access, operator/migration connectivity, instrumentation, model/API compatibility, image pulls and required per-service contracts remain pending. Platform/database backup and telemetry retention do not implement the 365-day evidence policy. Read the [documentation baseline](documentation-baseline.md) for source and evidence limits.

## Artifact and responsibility boundary

| Requirement | Value | Source and status |
|---|---|---|
| Delivery unit | Container image for this service. | Documented fact: `deployment.containerization`, ADR-001/018. |
| Immutable identity | Associate the image with its source commit and digest; promote the same digest through the staged delivery flow. | Documented organization target: CONTRIBUTING artifact promotion. No digest, version, image or automation exists. |
| Release/version | Policy targets Conventional-Commit-derived SemVer and protected `vX.Y.Z` tags after staging, without rebuilding. | Documented organization target, not configured release tooling. GHCR is selected in the integrated IaC baseline; image path/tag convention, build/publish responsibility and private-pull credentials remain pending under PD-035. |
| Ownership handoff | Proposed: this service's maintainers supply the tested image/digest and runtime/configuration requirements; IaC maintainers consume the approved delivery inputs for the agreed environment. | Proposal under PD-035; no named build/publish owner or accepted handoff contract is documented. |
| Promotion responsibility | Independent QA approval before staging and independent product-owner approval before production are policy targets. | Documented organization target. Concrete automation, environment mapping and operational owners remain unknown under PD-035; do not infer them from the sibling IaC implementation. |

Current target: only `dev` is selected for temporary academic MVP preparation (ADR-019 and IaC ADR 004). Local service integration/validation, complete readiness/access/cost review and explicit target/action authorization precede Azure writes. The dev procedural approval exception does not extend to future environments; retain independent QA/production promotion rules when commissioned.

## Execution

Documented fact: Documented REST/HTTPS input from nlp_log_processor (rel_nlp_correlation); output to recommendation_service is synchronous REST/HTTPS. No public ingress relationship is declared. Sources: service relationships listed in [architecture consumption](architecture.md), ADR-015/016/019 where applicable.

| Pending runtime value | Status and approval required |
|---|---|
| Listening port and bind address | Unknown, PD-035; framework defaults are not approved contract values. |
| Ingress scope, routing, TLS termination and caller authorization | Unknown beyond the documented caller/protocol above; PD-034/035, and PD-036 for workflow access where used. Do not add public exposure. |
| Liveness/readiness/startup probes | Paths, protocol, port, criteria and timings unknown, PD-035. No health endpoint is selected or implemented. |
| Startup | Initialization order, dependency retries, startup budget and migration coordination unknown, PD-035. |
| Shutdown | Graceful termination/drain behavior, time budget and in-flight work handling unknown, PD-035; broker consumers also depend on PD-033 re-delivery agreement. |

## Configuration and secrets

| Category | Known requirement and supplier | Status/source |
|---|---|---|
| Nonsecret integration settings | PostgreSQL evidence connection target and recommendation_service address. Configuration names and values are unknown under PD-034/035. Actual environment values and their suppliers are not assigned. | Targets/capabilities are documented in the model; exact settings and supplier allocation are unknown. |
| Secret/authentication requirements | The PostgreSQL foundation is Entra-only; runtime token handling, database grants, service-to-service authentication and suppliers remain pending (PD-027/034/035). | Unknown except explicitly documented mechanisms; PD-027/035. Secret storage is intentionally undecided. |
| Delivery of values | Proposed: backend maintainers define/validate the required configuration interface; the authorized environment/integration owners supply approved values through the agreed IaC handoff. | Proposal, PD-035; specific people/teams, rotation and distribution mechanism remain unknown. |

No application environment-variable names, credentials, secret store or runtime defaults are approved by this document. The system-assigned ACA identity lifecycle is selected in IaC; its creation, access grants and user/workflow authentication remain separate requirements.

## Access and integration

| Downstream | Intended operation and boundary | Canonical source |
|---|---|---|
| `postgresql_operational_store` | Data plane: read/write owned tables only; no database control-plane authority. | `rel_correlation_postgresql`; PostgreSQL |
| `recommendation_service` | Data plane: submit correlated context; no arbitrary writes to downstream storage. | `rel_correlation_recommendation`; REST/HTTPS |

Documented fact: incoming consumers/callers are listed in [service relationships](architecture.md#service-relationships). Protocols and directional operations above describe data access, not granted permissions. Control-plane access for Azure/GitHub provisioning or administration is not part of the service runtime contract; no grants are authorized. Exact credentials, audiences, scopes, network rules and permission sets remain unknown (PD-027/034/035 and PD-036 if used). Embedding-provider descriptions do not authorize undeclared direct calls.

## Data lifecycle

Documented ownership from ADR-018 and `model/domain.yml.entities`:

`evidence.correlated_contexts` (confirmed)

One physical PostgreSQL instance is shared; queries and commands use logical CQRS. Writes are confined to owned tables; shared schemas do not grant cross-owner writes. Physical FKs may exist within a schema; cross-schema references are logical IDs, with no ORM navigation across contexts (ADR-018, DD-014, PD-031). `audit.audit_events` has shared cross-cutting ownership and is not assigned exclusively to this repository; exact shared audit-write mechanics remain pending PD-035. Inferred `pipeline_access_grants`, where listed, preserve `ia_only`/inferred status and pending PD-007/008. No persistent schema is added for technical processing services without documented ownership.

**Proposal (PD-035):** the owner of each table authors and validates its migrations; IaC consumes approved compatibility and execution requirements without owning functional schema changes. Migration author/runner approval, sequencing, execution mechanism, rollback/recovery and deployment ordering are **unknown**; Flyway is selected for Java services with owned tables by the session-authorized [Java/Spring conventions](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/development/java-spring-conventions.md); no migration runner or job is selected. Services without owned tables do not acquire migrations through that choice. Services without owned tables have no owned-schema migration payload; do not infer new tables to fill the gap.

**Documented facts:** logical references must be validated through existing contracts (PD-031). Logs, diagnoses and evidence have the documented 365-day retention policy (ADR-013/016/018, INV-009, domain RET-001); do not extend that duration to corpus, configuration or audit. Policy evidence and purge mechanisms remain PD-017/030.

**Unknown (PD-033/034/035):** accepted message/API/schema compatibility rules, supported producer/consumer version skew, migration rollout sequencing and validation evidence. No proposed DTO, class design or embedding dimension is treated as an approved contract. For knowledge storage, vector dimension and corpus updates remain PD-005/006; no migration can assume either.

## Operations

Documented facts: Azure Monitor and Application Insights for application telemetry (ADR-002); correlationId, traceability and cross-cutting functional audit are model requirements (`audit_trail`, CTRL-003/004). Functional audit in PostgreSQL is distinct from technical telemetry. Evidence protection before NLP/RAG/LLM, read-only GitHub access and human control remain applicable invariants.

Unknown under PD-035: application instrumentation/token handling, log format/export, sampling, alerts, dashboards, operational ownership, SLOs, replicas, autoscaling triggers, concurrency, application CPU/memory/storage, actual region/names and availability budgets. The integrated foundation choices above do not settle those per-service contracts. Model evaluation targets are not operational SLOs. PD-029 also preserves unresolved operational degradation/version-update mechanisms for AI-related capabilities. No per-service resources or executable deployment configuration are inferred.

## Approval and unresolved decisions

Readiness blockers for the affected increment: [PD-035](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-032](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-027](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-010](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-031](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md), [PD-034](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md). PD-033/034/036 apply only to the exchanges this service uses. These link to existing pending IDs; this record neither creates nor closes decisions.

Before IaC consumes this contract, provide:

1. A versioned contract revision with actual image source commit/digest, runtime versions, required execution/configuration/access inputs and explicit backend/IaC responsibilities for the deployment increment.
2. Traceable decisions/evidence for each required unknown or accepted proposal, including relevant event/REST/workflow contracts, credentials/secret handling and data/migration compatibility. Resolve or explicitly accept the source-provenance limitations affecting that increment; absent fields are not defaults.
3. Identifiable approval by the authority recorded in the committed pending-decision register: CODEOWNERS **Jhon Galvez and Ramiro Guzman** for backend–IaC coordination and applicable contracts. The rule for one versus both approvals and GitHub handles/enforcement is unknown; do not invent CODEOWNERS configuration. Reference the committed source revision and provide identifiable approval evidence for this delivery contract; source availability alone does not approve it.
4. The applicable independent environment promotion approvals and separate authorization for any infrastructure action. Contract approval alone does not authorize deployment.

**Approval evidence today: none for this service's delivery contract.** ADRs retain their recorded Accepted status; missing individual acceptance records remain PD-014. This document is sufficient to expose gaps while repository initialization proceeds; it is insufficient to claim deployability.

## Local contract proposal for decision

**Candidate, 2026-11-11; not adopted or implemented.** Source revisions and task
scope are in [architecture](architecture.md#local-implementation-task--2026-11-11).
No new agreement was found that closes PD-010/031/034 for this receiver. The
independent table/build increment does not accept the following proposals.

### NLP → Correlation: existing producer and proposed receiver

The implemented NLP caller uses HTTPS `POST /internal/v1/processed-evidence`,
`application/json; charset=utf-8`, body `contract_version: "2.0"`. The path version
and body version are distinct. See the exact candidate body in
[NLP integration contracts §3](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/docs/integration-contracts.md#3-correlation-handoff)
and its serializer in
[correlation_http.py](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/src/pipesor_nlp_log_processor/infrastructure/correlation_http.py).
There is no Correlation receiver yet.

| Surface | Producer behavior / proposed receiver requirement |
|---|---|
| Identity and tracing | `message_id` UUID text equals `Idempotency-Key`; `correlation_id` opaque text equals `X-Correlation-ID`. Valid W3C `traceparent`/`tracestate` are technical headers, never identity or authorization. Reject mismatches without echoing values. |
| Authentication | Bearer token; NLP proposes Entra workload identity. Receiver must validate issuer, audience, authorized service/role and trusted resource-scope binding. Exact values and authority supplying tenant/capture scope remain unagreed; no allow-all mode. |
| Capture / operational context | Preserve `source`; `capture_context` tenant UUID, account/installation decimal strings and `scope_revision`; repository/workflow decimal strings; run decimal string, positive attempt, status/conclusion; commit SHA; nullable `service`/`environment`. Full tuple must agree with verified operational references. |
| Logical IDs | Preserve `pipeline_id`, `execution_id`, `incident_id`, `evidence_id` as UUIDs. Pipeline IDs already have integrated deterministic rules; do not regenerate or change them here. NLP's computed evidence UUIDv5 is still an owner-level agreement gap with Log/Event. None proves existence or access. |
| Times / provenance | Preserve original UTC RFC3339 `occurred_at` and `source_updated_at` including up to nine fractional digits. Keep external IDs as strings; `sequence` may exceed 32 bits. `created_at` is an independent technical insertion time, not either source timestamp. |
| Processed evidence | `stage`, optional `job`, nullable `sequence`, `sanitized_summary`, `sanitized_content`, `template`. Protected/minimized input only; template is not a confirmed error cause. The prefix `sanitized_` is not proof of safe content. No raw logs, credential fields or arbitrary artifacts. |
| Compatibility / limits | Proposed: exact supported `contract_version=2.0`, unknown additive optional fields ignored consistently with NLP, unsupported major rejected. Required-field type/meaning changes are breaking. Request bytes, field/collection sizes and total processing timeout require explicit agreed numeric limits; no unbounded public parser is enabled meanwhile. |

The published
[NLP fixture](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/tests/fixtures/ingestion-evidence-v2.json)
is an **Ingestion input fixture**, not a Correlation request. It includes
`123456789`/`987654321` fractional nanoseconds, external IDs larger than signed
64-bit and `sequence=4294967303`. Future receiver checks must pass that artifact
through the actual NLP transformation/serializer and compare exact resulting
IDs, strings, nullability, timestamps and provenance. This increment has no
receiver parser and does not claim fixture interoperability validation.

### Proposed completion and recovery semantics

Recommendation: retain synchronous processing. `200 {"status":"accepted",
"message_id":"<original>"}` means the authorized context was reference-validated,
its sanitized snapshot and receipt committed durably, and Recommendation
confirmed the same operation according to its agreed durable reception contract.
It does not mean a diagnosis is ready. A local commit alone is insufficient.

`200 {"status":"duplicate","message_id":"<original>"}` means the same scoped
message and semantically identical content already satisfied **all** of that
commitment. A pending receipt is not a duplicate success. Reuse with different
content is `409` with a stable `message_conflict` code, no payload values. Scope
is the trusted authenticated producer/resource partition plus `message_id`;
the exact encoding and persistence key must be approved, not derived from an
untrusted tenant field. Replays must be authorized again before any result is
revealed.

This needs a durable state machine: prepare snapshot/receipt atomically; send the
same operation/snapshot to Recommendation synchronously; persist delivery
confirmation; only then return success. If the local response is lost after
confirmation, replay returns duplicate. If Recommendation commits but its
response is lost, replay must use its stable idempotency key and recover its
durable result before marking local delivery. If local preparation commits and
Recommendation fails, return retryable failure and retain pending state; replay
resumes that same snapshot rather than creating another. Concurrent requests
must converge through an approved database uniqueness/claim strategy, not a
process-local lock. Neither service may infer exactly-once HTTP delivery.

The current seven-column `correlated_contexts` table cannot express this state:
there is no message key, request digest, trusted scope or delivery phase. Proposed
model direction: a separate Correlation-owned receipt if snapshots may contain
several input messages; unique trusted scope/producer/message key, canonical
content digest/version, snapshot ID and preparation/delivery state with technical
times. This is **not** a migration specification or permission to create a table.
The owning model must first settle physical fields, safe replay/provenance
material, atomicity, retention, concurrency and recovery. Do not store raw input
JSON or add it to `correlated_contexts`. Hashing a message does not preserve its
provenance or make sensitive text safe. Canonical comparison must include all
significant context/provenance/processed content, exact source time precision and
null/absent semantics, excluding authentication tokens and technical trace headers.
Canonicalization/hash format is a required agreement, not chosen in this increment.

Proposed errors, compatible with NLP's current error classification:

| Condition | Proposed result and obligation |
|---|---|
| Invalid body/header, incompatible version | `400`, stable code and field paths only; no durable acceptance. |
| Missing/invalid service token | `401`, generic response; NLP abandons current message and stops consumption. |
| Unauthorized service or resource scope | `403`, generic response without reference-existence disclosure; NLP abandons/stops. |
| Same scoped ID, different content | `409 message_conflict`; permanent rejection, never duplicate. |
| Temporarily unavailable or not-yet-arrived references | `503 references_unavailable`, no success and no fabricated records. Requires an actual approved way to validate references. |
| Authoritatively inconsistent references | `400 reference_mismatch` after authorization, no values; no snapshot. Distinguishing absence from inconsistency requires the reference contract. |
| Dependency/network/commit/delivery failure | `503`, safe stable code. Pending durable preparation is resumed as above; an ambiguous DB commit is resolved by its stable key. |
| Capacity or rate limit | `429`, safe code; bounded numeric `Retry-After` only within the caller deadline. Numeric budgets remain PD-035 inputs. |

NLP retries timeouts, connection errors, `429` and `5xx`; other `4xx` are permanent
except its special `401`/`403` stop behavior. Thus a reference that may simply be
late must not receive a permanent not-found response. A never-created reference,
retry horizon and safe operational disposition require a separate explicit
policy; an infinite retry loop is not proposed. No extra broker or polling
relationship is introduced.

### Correlation → Recommendation proposal

Proposed single synchronous submission on the existing
`rel_correlation_recommendation` (the diagnostic-api relationship is its component
projection, not a second delivery): `POST /internal/v1/correlated-contexts`, JSON
`contract_version: "1.0"`, stable `operation_id`, trusted authorized scope envelope,
and `context` containing the seven snapshot fields (`id`, `incident_id`,
`execution_id`, `evidence_ids`, `summary`, `correlation_id`, `created_at`). The URI
and envelope are proposals, not an endpoint discovered in Recommendation. The
scope supplier/encoding must be jointly specified; a body tenant ID is not proof.
For an initial minimal context, `summary` should contain only agreed safe
operational metadata, with no copied excerpts or template-based diagnosis.
Whether Recommendation additionally requires processed signals/source timestamps
must be decided before adopting this minimum; no evidence lookup API is implied.

Proposed headers: Entra Bearer identity for a Recommendation-specific audience and
role, `Idempotency-Key=operation_id`, original `X-Correlation-ID` and W3C trace
context. Recommendation must enforce resource scope and durably recognize the
operation/content before returning `200` with `status=accepted|duplicate` and the
same `operation_id`/`context_id`. This receipt means durable context reception,
not completed diagnosis. A pending/unconfirmed request cannot be declared
accepted by Correlation. Conflicting content is `409`; authentication and
temporary/permanent failures follow the same distinctions above. Timeout means
unknown result, so recovery resends the same operation, never a fresh diagnosis
request ID. Exact DTO, reception behavior and receipt storage belong to
Recommendation under PD-010/034 and must be implemented there by its owner.

### Decisions needed and responsible parties

Each row is a specific decision; none is approval by analysis or by this local
implementation. Jhon Galvez and Ramiro Guzman are the documented contractual
authority for PD-010/031/034/035; the one-versus-both approval rule is unspecified.

| Exact question / impact | Options and recommendation | Authority / dependent work |
|---|---|---|
| Accept NLP's v2 route/body/headers and the completion/recovery semantics above? Without this, broker completion can lose downstream work. | Local durable acceptance with deferred delivery, or synchronous confirmed delivery plus recoverable receipt. Recommend the latter to preserve the canonical synchronous flow. | PD-034, Jhon/Ramiro; NLP and Correlation owners coordinate. Blocks receiver, not owned-table foundation. |
| Where will scoped message identity, content comparison and delivery state live, with what physical constraints, precision and retention? | Evolve snapshot or separate owner receipt. Recommend a separate receipt if grouping is selected; first approve source-model evolution and canonical comparison. | PD-031/034/035 and model owner. Blocks durable receipt/acceptance; this task cannot modify docs repository. |
| How can Correlation verify authorized tenant/repository/run/attempt/incident/evidence membership through the existing flow, including not-yet-arrived data? | Approved owner-backed proof carried by the existing exchange, or an explicitly approved architecture/contract change if new calls are needed. Recommend retaining current relationships and specifying a verifiable carried proof if feasible; do not invent it. Then choose temporary rejection versus durable pending reconciliation. | PD-031 Jhon/Ramiro for contract; Pipeline and Log/Event owners implement their portions. No usable validation contract exists today. |
| Does Log/Event accept the UUIDv5 evidence-ID rule already computed by NLP? | Adopt the proposed rule or agree an owner-issued identity through the declared exchange. Recommend confirming the compatible rule with Log/Event; no ID changes here. | Log/Event owner, PD-031/032/033; no owner approval found. |
| Accept the proposed Recommendation request, minimal information, scope binding and durable response? | Self-contained safe snapshot versus a reference requiring an agreed retrieval relationship. Recommend a single self-contained submission; settle needed signals and idempotency first. | PD-010/034 Jhon/Ramiro with Recommendation owner. Blocks synchronous adapter/recovery tests. |
| What is the first correlation unit and what causes delivery/version closure? | One verified evidence item per snapshot, or multi-evidence accumulation with explicit version/closure. Recommend deciding the one-item first slice; no window or score. | Architecture lists dimensions only; algorithm approval authority not identified. Contract approval alone must not be assumed to assign it. |
| What issuer/audiences/roles and trusted resource-scope evidence, payload limits and timeout/retry horizons apply? | Agree explicit fail-closed values versus keep integration disabled. Recommend explicit values before enabling REST; no permissive defaults. | PD-034/035 Jhon/Ramiro with security/backend/IaC owners; not settled by Entra as a technology choice. |

Concrete examples to decide the correlation rule: identical template/commit/time
across different tenants, repositories, runs or attempts must remain separate;
two distinct messages in one verified execution may either produce two snapshots
or one explicitly versioned snapshot; a repeated identical message is receipt
recovery, not new evidence; a late distinct message must have an agreed new-version
or new-snapshot outcome. No timestamp window, score, severity inference or close
condition is implemented here.
