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

Locally adopted DD-023 adds `evidence.correlation_groups` and
`evidence.correlation_receipts`, owned solely by this service. The source model
and generated constraints are local changes to `pipesor-docs` on top of the
integrated revision recorded below; they are not a claimed published baseline.

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

**Candidate, 2026-11-11, with partial local adoption below.** Source revisions and task
scope are in [architecture](architecture.md#local-implementation-task--2026-11-11).
No new agreement was found that closes PD-010/031/034 for this receiver. The
independent table/build increment does not accept the following proposals.

### Local remediation agreements and implementation

Following the assessment, the user explicitly adopted cumulative immutable
group revisions and separation of stable protected source from mutable NLP
enrichment. These are local agreements, not integrated cross-service contracts
or closure of PD-010/031/034. The user authorized local edits in Correlation,
NLP and `pipesor-docs` where necessary; other neighbors remain read-only.

Group identity binds trusted machine scope, exact execution/attempt and automatic
ORIGIN incident. Each distinct verified evidence reference expands the set and
creates a revision; an existing member does not. The first revision may contain
one member and subsequent revisions multiple members; snapshots are not
independent single-evidence outputs. No grouping across attempts, similarity
matching, time window or completeness/closure signal is introduced.

The earlier domain-only increment passed twelve checks. The subsequent approved
storage increment now implements group coordination, immutable cumulative
snapshots, scoped message receipts and monotonic confirmation, with V2 following
DD-023. V1 is unchanged. See [README](../README.md#development-and-verification)
for current executed checks. The store does not authorize the opaque scope or
validate external references. Fingerprints are supplied technical inputs; no
real request comparator, listener or Recommendation adapter is enabled. The next
decision concerns the safe diagnostic representation to preserve and deliver.
The internal adapter assigns group and snapshot UUIDv4 values with
`UUID.randomUUID()` before insertion. Supplied message/evidence and operational
IDs are preserved; this does not change upstream identity algorithms. The receipt
uses its natural scoped message key and needs no extra UUID.

### Durable grouping proposal after the reuse audit

**Locally approved for implementation by the user, 2026-11-11.** After the reuse
audit and concrete proposal, the user explicitly approved this representation
and asked to continue to the next material decision. This adopts the two owned
technical tables, snapshot association, derived revision, shared confirmation
pointer and serial preparation policy. It is not integration into main,
deployment approval or evidence of implemented/live reception.
The consulted source is Docs `65792190d2ee32f0721d22848cd62cee1fd0d630`, plus the
separately inspected local agreements in its pending-decision register; that
earlier overlay adopted grouping; the subsequent approval now also authorizes
this schema and delivery-order increment locally. Local
`CorrelationGroup` and its tests are implementation evidence of the pure rule,
not evidence of durable groups. Other consulted service revisions remain those
in [architecture](architecture.md#revalidated-sources-and-requirements).

Primary references are the [persistent model](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain.yml),
[DD-003/014/021](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md),
the [Pipeline receipt migration](https://github.com/pipesor-org/pipesor-pipeline-service/blob/main/src/main/resources/db/migration/V5__create_execution_event_receipts.sql),
the [Ingestion ledger](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/blob/main/src/main/java/org/pipesor/ingestion/capture/infrastructure/persistence/jpa/JpaCaptureLedger.java)
and [local grouping code](../src/main/java/org/pipesor/correlation/context/domain/model/aggregates/CorrelationGroup.java).

#### Existing data and what can be reused

| Existing fact / source | Reuse and limit |
|---|---|
| `correlated_contexts`: seven columns in `model/domain.yml`, DD-014, local V1 and JPA mapping | Use each row as the immutable snapshot of one cumulative revision. Keep its evidence array, summary, incident/execution references and tracing; do not copy them into a second revision table. |
| `evidence_ids UUID[]`, DD-014/A-006 | Already records snapshot membership. No additional membership/join table is needed for the adopted rule. Logical evidence references remain owned by Log/Event. |
| Local `CorrelationGroup.revision()` | Already equals membership cardinality. Under the adopted one-new-member-per-revision rule, a separate stored revision counter is unnecessary. |
| `diagnoses.version`, DD-003 | Versions Recommendation's diagnostic hypotheses per incident, not Correlation's groups. It is neither the group revision nor evidence of downstream receipt. |
| Ingestion `capture_receipts`, `capture_observations`, `publication_outputs`, DD-021 | Recover capture and broker publication, not Correlation transactions or Recommendation delivery. Reuse the transaction/claim lessons, not those owners' tables or payloads. |
| Pipeline `ops.execution_event_receipts` in its V5 | Records snapshot processing, comparison and dispositions. This is a pattern for owner-local receipts; it cannot store Correlation's effects and is not a group head. |

No integrated Correlation group, head, message receipt or delivery record was
found. Current `correlated_contexts` does not contain trusted scope/group,
producer/message key, input comparison or delivery confirmation. Similar names
in another owner's schema do not provide those facts for this service.

#### Recommended minimum representation

The approved minimum uses **two new owned technical tables and one association
on the existing snapshot**, materialized in the source model before migration:

| Proposed representation | Minimum facts and purpose |
|---|---|
| `evidence.correlation_groups` | Owner-assigned `id`; trusted opaque `scope_key`; `execution_id`; `incident_id`; nullable `confirmed_context_id`; technical `created_at`/`updated_at`. Unique `(scope_key, execution_id, incident_id)`. The row anchors group identity and the short transaction lock. The pointer records the last durably confirmed reception, not another copy of snapshot content. |
| Existing `evidence.correlated_contexts` | Keep the seven snapshot fields and add `group_id`. Derive the ordinal from the number of evidence IDs, as the domain does. Each new row is an immutable cumulative revision. The latest prepared revision is queried from these rows; no persisted head pointer/counter is needed. |
| `evidence.correlation_receipts` | Trusted `scope_key`, authenticated stable `producer_key`, source `message_id`, individual `evidence_id`, original message `correlation_id`, referenced `context_id`, private versioned input-comparison fingerprint and technical `created_at`. Unique scoped producer/message key. Insert atomically with its snapshot association; do not duplicate delivery status in each receipt. |

These names/fields are the adopted local implementation scope. The fingerprint
is a **private Correlation implementation** over the
significant protected input already received, excluding mutable template and
technical credentials/traces. Its exact field encoding/version, precision,
null/absent rules and algorithm are ordinary implementation specifications to
document and test when the actual input comparator is introduced. Storage tests
may use synthetic fingerprints without claiming real request comparison.
It is not a new NLP wire hash, authentication or proof of owner persistence.
Do not store the whole request, raw logs or a duplicate excerpt in a receipt.

`scope_key` is required on the receipt to enforce the message key across groups;
it must agree with the referenced snapshot's group. The receipt's `evidence_id`
and message `correlation_id` are individual source associations: an accumulated
snapshot array and its single revision trace cannot reconstruct them. This is
not a second membership table. Several receipts may reference the same snapshot;
do not assume producer message ID and evidence ID are necessarily one-to-one
before the owner contract settles that identity.

Use `correlated_contexts.id` as the proposed downstream operation identity.
Another independent operation UUID would add a mapping without a demonstrated
need. Proposed handoff metadata includes group ID and derived revision, distinct
from Recommendation's diagnosis version. The wire/response agreement remains
PD-010/034, and no Recommendation implementation is claimed.

The existing `incident_id`/`execution_id` snapshot fields stay because DD-014 and
the output snapshot use them. Their repetition relative to group identity must
be constrained to agree, not treated as another source of mutable context.
All new relational constraints are within Correlation's ownership; no FK or
ORM navigation to `ops`, `evidence_items` or diagnostic tables is proposed.

#### Concurrency, order and recovery

The user-approved ordering policy is: **prepare the next revision
only after the current prepared revision has confirmed durable reception**.
This avoids a second delivery queue or window/closure mechanism, at the cost of
blocking that group's progress during downstream unavailability. This was adopted
with the durable-state proposal; the earlier grouping-only agreement did not
select it by itself.

1. Authenticate/authorize and verify references under their agreed contracts.
   In a short local transaction, find/create and lock the unique group row,
   check the scoped message receipt and read the latest snapshot.
2. A matching existing message recovers its exact original `context_id`;
   significant-input conflict does not change anything. A new member creates
   exactly one cumulative snapshot and receipt atomically. An existing member
   under another message must not create a revision; the receipt's association
   with the original member revision needs to be explicit in the final contract.
   If the latest revision is unconfirmed, do not prepare another one or declare
   the incoming new message accepted. Recover/retry according to the agreed
   deadline and operational policy, without an autonomous worker by inference.
3. End the transaction before HTTP. Submit the frozen accepted representation
   under the same context/operation ID on every attempt. Concurrent/repeated
   sends are safe only if Recommendation durably enforces that identity/content.
   No process-local lock or open SQL transaction spans network I/O.
4. After authoritative downstream confirmation, advance the group's confirmed
   pointer in another short transaction. It must reference a snapshot of this
   group and never regress on a late acknowledgement. A lost response resumes
   the same operation; neither snapshot insertion nor a pending receipt proves
   successful delivery.

The confirmation pointer is new delivery progress, so it cannot be inferred from
which snapshots exist. Storing it in the group preserves the existing snapshot's
business immutability and avoids adding mutable delivery columns to its
`@Immutable` mapping. Group `updated_at` follows actual pointer changes. A
nullable `delivery_confirmed_at` on each snapshot is an alternative, but would
require deliberately changing its physical mutability/technical mapping and
timestamp rules; it is not needed alongside the recommended pointer.

Acceptance order is not diagnostic completion order. Recommendation still needs
an agreed receipt/content contract and a policy preventing old group revisions
from becoming the current result incorrectly. Its `diagnoses.version` cannot be
silently equated with the group ordinal.

Required invariants include one snapshot per group/cardinality, a one-dimensional
nonempty UUID set without null/duplicate members, and exact one-member superset
growth under the group lock. An expression index/constraint on cardinality alone
does not prove the cumulative chain; restoration, adapter writes and database
protections must be checked together. The confirmed pointer and receipt/snapshot
scope/context associations require own-context membership constraints.

#### Redundancy deliberately avoided and outstanding gates

There is no new revision table, membership table, duplicated head/current counter,
extra operation UUID, delivery status per receipt, NLP hash or NLP cache. The
snapshot array repetition itself is intentional: revision N contains its exact N
members. Across N revisions this stores N(N+1)/2 UUID references. Payload limits,
capacity/backpressure and retention must be measured/explicit before activation;
switching to delta-only history would change DD-014 and is not proposed here.

Do not fabricate scope, producer/message identity or receipt history for existing
V1 rows. The migration plan must inspect actual stored data, including empty
arrays currently allowed by V1, and either use an approved evidence-backed
mapping or refuse that migration case. Keep V1 immutable. Retention must preserve
snapshots referenced by valid receipts and the confirmation pointer, without
silently restarting a purged group at revision 1 or extending the evidence
owner's policy. Receipt replay horizon and eventual metadata disposal still
require an agreed lifecycle; no unlimited retention or cascading deletion is
selected by this proposal.

A fingerprint cannot recover discarded enrichment. Before enabling the business
write/handoff path, Recommendation must identify the safe accepted representation
it needs. This does not block the approved isolated storage increment. Reuse
the frozen snapshot if the seven fields suffice; if extra protected signals are
required, approve their minimum recoverable owner representation explicitly.
Do not add a generic payload store to anticipate that answer.

The approved physical increment implements the representation and serial
policy, with conservative refusal of unmapped legacy rows and no purge/runtime
activation. A duplicate member under a different message is associated with the
original member revision; source comparison remains per scoped producer/message
key. Technical choices do not require reopening that approval. The next material
decision is the minimum safe diagnostic representation to freeze and deliver;
runtime identity, owner validation and live receipt integration remain separately
bounded work, not claims made by these storage checks.

The acceptance criteria cover concurrent first creation/member additions without
forks; matching replay preserving context ID/result; changed input leaving no
effect; rollback without partial receipt/snapshot; crash after preparation and
lost downstream confirmation; late acknowledgements without pointer regression;
multiple receipts sharing one revision; crossed scope/group/member rejection;
and migration/retention refusal when provenance or valid references would be
lost. The implemented storage subset and actual results are recorded in README;
these local checks do not establish real owner validation or downstream receipt.

#### Next decision: the frozen diagnostic representation

The approved source model, migration and local store are implemented. Before
building the business write/handoff path, choose the information needed by
Recommendation. [ADR-015](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-015-diagnostic-runtime-boundaries-and-orchestration.md)
uses sanitized evidence and RAG context for inference. The
[proposed diagnostic design](https://github.com/pipesor-org/pipesor-docs/blob/main/model/design.yml)
also mentions sanitized evidence, but does not approve an input DTO or specify
fields. There is no agreed Recommendation-to-evidence lookup to recover omitted
signals from IDs alone. A successful storage test cannot establish diagnostic
sufficiency.

| Decision option | Effect |
|---|---|
| Seven snapshot fields, with an agreed synthesized `summary` | Reuses the current stored result entirely; diagnosis has only that summary and evidence IDs. Its usefulness/limitations must be accepted explicitly; no hidden lookup or reconstruction from a fingerprint. |
| Snapshot plus a minimal protected signal per evidence (recommended) | Preserve `evidence_id`, stage, sanitized summary and minimized sanitized content from first acceptance, with their original provenance. This retains diagnostic error detail without transmitting raw logs or relying on an unstable template. Approve the exact recoverable representation before extending storage; avoid a generic payload store or repeating full signal content in every cumulative snapshot. |

The concrete approval question is whether to include those minimal protected
per-evidence signals or deliberately constrain diagnosis to the existing
snapshot summary. A template is optional enrichment, not a cause or a prerequisite
for grouping; no NLP wire hash/cache is needed for either option. No new payload
fields, storage copies or receiver contracts are implemented in anticipation of
the answer. Runtime authorization/reference validation remains a separate boundary
requirement, not something this decision or a technical confirmation bypasses.

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
| Capture / operational context | Preserve `source`; `capture_context` tenant UUID, account/installation decimal strings and `scope_revision`; repository/workflow decimal strings; run decimal string, positive attempt, status/conclusion; commit SHA; nullable `service`/`environment`. Verify operational identity/membership through the agreed owner contract; historical signal values need not equal the owner's latest projection. |
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
model direction is refined by the [durable grouping proposal](#durable-grouping-proposal-after-the-reuse-audit):
a Correlation-owned receipt records the scoped producer/message key, individual
source association, versioned input comparison and snapshot reference; the group
records the shared delivery confirmation pointer. Delivery state is derived,
not duplicated per receipt. This is **not** a migration specification or
permission to create a table.
The owning model must first settle physical fields, safe replay/provenance
material, atomicity, retention, concurrency and recovery. Do not store raw input
JSON or add it to `correlated_contexts`. Hashing a message does not preserve its
provenance or make sensitive text safe. Comparison must cover the significant
received protected fields: capture/context, operational and logical identity,
original correlation/source times, source, commit, service/environment,
stage/job/sequence and sanitized summary/content. Separate mutable `template`
enrichment from those fields; exclude authentication tokens and technical trace
headers. Preserve agreed null/absent semantics.
Canonicalization/hash format is a required agreement, not chosen in this increment.
NLP's process-local Drain3 can change `template` when the same broker message is
processed again; see the [data-readiness assessment](#data-readiness-assessment-for-the-first-correlation-increment).
Comparison of processed content must not silently become rejection of legitimate
reprocessing or mutation of an accepted snapshot. The adopted rule preserves
the first accepted result on matching replay; changed significant protected
input still conflicts. Physical comparison/storage and recovery require their
contract before the business receiver is enabled.

### Replay ownership and digest overlap

The user's follow-up requested checking whether a source digest in NLP would
duplicate existing work. **No new digest or wire field is selected for this
increment.** The demonstrated issue is mutable `template` during reprocessing;
the receiver already gets the protected content and context needed to distinguish
that enrichment from significant input changes.

| Existing responsibility | Why it remains local / cannot substitute for another owner |
|---|---|
| Ingestion webhook `body_digest` | Identifies the authenticated webhook, not the emitted evidence or NLP request. |
| Ingestion observation fingerprint | Includes protected observation facts such as stages, branch and locator that NLP does not receive in full. It recovers the frozen publication plan. |
| Ingestion output `payload_digest` | Checks its stored exact publication bytes. `PublicationOutput.applicationProperties()` exports only `eventType` and `schemaMajor`; this digest is not in the current wire. |
| Pipeline message/content digests | Protect its own execution snapshot effects/versioning, not Correlation's evidence groups or Recommendation delivery. |
| Correlation accepted-input comparison and receipt | Must protect this owner's group/revision and downstream effects. It cannot be replaced by upstream broker acknowledgement. NLP needs no durable duplicate store. |

Forwarding an upstream digest would require a contract change; recomputing it in
NLP would be additional work without a demonstrated need for the current fix.
The minimum receiver comparison uses existing significant protected fields;
enrichment-only changes resume the original accepted result and cannot replace
its template or create a group revision. Never use template similarity as group
identity or treat changed protected content as a matching replay.

This comparison does not prove byte identity before sanitization: differences
discarded by that transformation are no longer visible to Correlation. It also
does not automatically accept sanitizer upgrades that change significant output
under the same message ID. Original-byte attestation or versioned reprocessing
would require an explicit additional guarantee and compatibility decision. A
digest supplied by NLP would not itself prove authorization or owner persistence.
This limit avoids adding a second pipeline of canonicalization, tokens or caches
to fix the demonstrated enrichment issue. No NLP application code changed.

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

### PD-031 decision proposal: owner-verified references

**Proposal for review, 2026-11-11; not accepted or implemented.** This section
refines the reference-validation question below. It does not approve the receiver,
the one-evidence correlation unit, new relationships, grants or schema changes.
Sources retain the revisions recorded in [architecture](architecture.md#revalidated-sources-and-requirements):
Docs `6579219`, Pipeline `84f7ac0`, Log/Event `d1f0277`, Ingestion `1066785`
and NLP `1d6e54f`; the inspected checkouts were clean. In particular, consult
[PD-031](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md),
[ADR-018](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/decisions/ADR-018-application-architecture-ddd-cqrs.md),
[logical references and DD-014](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md),
[NLP delivery ordering](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/docs/integration-contracts.md#2-delivery-duplicates-and-ordering)
and [Ingestion's explicit local approvals](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/blob/main/docs/deployment-contract.md#explicit-local-approvals--2026-11-10).
The [data-readiness assessment](#data-readiness-assessment-for-the-first-correlation-increment)
below narrows the minimum required guarantee and corrects historical scope
assumptions; full historical-message attestation is not a prerequisite of DD-014.

#### Recommendation and architectural cost

Recommend **read-only validation by each data owner before creating a correlated
snapshot**. Propose two internal synchronous relationships: Correlation → Pipeline
for operational membership and Correlation → Log/Event for persisted evidence
membership. These relationships are absent from the current model. An explicit
architectural decision and model update must precede their implementation;
PD-031 contract approval alone does not amend ADR-018. No route or permission
is enabled by this proposal. The NLP → Correlation → Recommendation business
flow remains synchronous; validation adds dependency reads, not a second context
delivery or diagnosis owner.

| Alternative | Assessment |
|---|---|
| Owner validation queries (recommended) | Owners read their own data and can report post-commit facts. Adds two relationships, latency and availability dependencies; scope/provenance sources still need agreement. |
| Post-commit owner attestations carried by NLP | Could avoid reads during Correlation, but needs an agreed acknowledgement/routing path, issuer trust, content binding, freshness and replay semantics. The existing graph has no such return path; it is not a smaller change today. |
| Trust Ingestion/NLP IDs or a signature on the original event | Proves neither owner persistence nor authorized membership. Deterministic UUIDs and broker arrival are insufficient. Reject as a reference-validation mechanism. |
| Read foreign tables directly or introduce cross-context FKs | Requires changing current access/ownership contracts; couples Correlation to foreign schemas and does not establish authorization. Not recommended. |

#### Validation obligations and trusted scope

Authenticate NLP and establish its allowed machine resource scope before asking
about references. A payload tenant, installation, `scope_revision`, trace ID or
valid service token alone cannot establish resource access. Each owner must also
authenticate Correlation and enforce that it may validate the requested resource.
Human dashboard grants must not be reused implicitly as machine capture rights.

Propose Ingestion's trusted admission/current-capture authority as supplier of
the machine scope, with Pipeline mediating through its existing
`rel_pipeline_ingestion_github_access` relationship **only after** a machine-scope
operation is explicitly agreed. The current human-access operation is not that
contract. Ingestion already has an approved operator-owned, versioned atomic file
authority and an implemented `GitHubFileAuthority`/`CurrentMachineScope` adapter;
that mechanism must not be reopened. Actual environment inputs, operator
assignment and the contract for other services to obtain scoped authority remain
pending. Do not infer a new configuration
service, replicated allowlist or additional Correlation → Ingestion relationship.
The contract must also specify how Log/Event obtains verifiable resource-scope
authority (for example, a bound owner confirmation or an explicitly approved
configuration handoff). An authenticated Correlation request containing scope
fields is insufficient by itself. No Log/Event → Pipeline/Ingestion call is
implicitly added, and no confirmation-signing/key mechanism is selected here.

Propose authorization checked for each new processing attempt, without a positive
authorization cache or implicit grace period. A revoked resource is denied; an
unavailable authority is unavailable, never allowed. Historical capture revision
is provenance, not current authorization. This proposed cutoff must be accepted
jointly with Ingestion's in-flight/recovery policy; it does not authorize deleting
already persisted snapshots or reversing downstream work. Once handoff has
started, ambiguous completion and replay remain part of PD-010/034 recovery.

The conceptual validation request binds the supplied logical IDs to the expected
tenant/capture context, repository/workflow, run/attempt and source provenance.
Fields, encodings, endpoint paths, numeric limits and compatibility must become
one owner-reviewed PD-034 contract before adapters are implemented. Proposed
obligations are:

| Owner | Facts it must verify from committed authoritative state |
|---|---|
| Pipeline | Pipeline belongs to the requested repository/workflow in the trusted scope; execution belongs to that pipeline and exact run/attempt; incident is linked to that execution through the applicable `incident_runs` relationship. The incident's pipeline matches its ORIGIN pipeline; an accepted VERIFICATION link may use another pipeline. If correlation admits that role, validate authorization for both resources without imposing pipeline equality. Eligible roles belong to the correlation rule. |
| Log/Event | Every referenced evidence item exists and its stored execution, incident and source agree with the authorized operational context, using the owner-approved evidence-ID rule. Return confirmation metadata only, without excerpts or raw logs. Exact message/capture attestation is a separate stronger guarantee; a reference check does not prove which bytes NLP processed. |
| Correlation | Require both confirmations for the same request context; preserve logical IDs and `correlation_id`; reject mismatched, incomplete or incompatible responses. Write only its owned sanitized snapshot and call Recommendation only after validation. |

**The current models cannot prove the stronger historical binding.** Pipeline does not persist
the full tenant/capture envelope; `evidence_items` does not persist the complete
capture context, producer message identity or exact source-version tuple carried
by NLP. Minimum membership validation needs an agreed scope contract and evidence
identity, not duplication of that entire envelope into each owner table. DD-014
preserves operational provenance through logical evidence IDs. Reuse existing
owner facts where they suffice; if stronger historical attestation is separately
required, any additional durable provenance/index must be specified in the
source model and its decisions first. No columns, receipts, UUID/hash algorithm
or canonicalization format are selected here. A content hash alone does not prove
membership or make protected text safe; processed NLP text is not necessarily
byte-identical to the stored upstream excerpt. PD-032/033 must settle the evidence
identity with Log/Event rather than assuming NLP's candidate UUIDv5 is owner-approved.

#### Independent arrival, failure and consistency

Propose the following semantic results; wire status/error mapping remains PD-034.
Owners return only stable codes and minimal confirmation data after authorization.
Bind each response to the validation request/version and expected context; it is
not a reusable authorization token. Missing or contradictory response binding is
an invalid confirmation, never acceptance.

| Result | Meaning and required Correlation behavior |
|---|---|
| `confirmed` | Owner read committed facts matching the full agreed binding. Both owners must confirm before snapshot preparation. |
| `not_ready` | A row or required owner binding is not yet visible, or an owner cannot distinguish late arrival from permanent absence. No snapshot/handoff or success; report retryable unavailability to NLP. |
| `unavailable` | Timeout, dependency/scope-source outage or unsupported validation capability. Same no-success behavior; do not downgrade validation. |
| `mismatch` | After authorization, existing authoritative facts contradict the requested binding. Permanent sanitized reference rejection; no snapshot/handoff. Absence alone never proves mismatch. |
| `denied` | Caller/resource access is not authorized. Generic denial without disclosing which references exist; preserve NLP's documented stop-on-authorization-failure behavior. |

Do not wait indefinitely for another consumer, fabricate upstream rows, correlate
by similarity or introduce a polling worker. Use the existing caller recovery path
within an agreed total deadline and retry horizon. Those numeric budgets and
exhaustion/disposition ownership must be approved under PD-033/034/035 before
activation; an indefinitely missing reference must not generate an infinite loop.
Pipeline may legitimately consume an event without creating an incident, for
example when monitoring is paused. The absence of a row cannot distinguish that
outcome from delayed consumption; the approved exhaustion policy must cover it.
This proposal introduces no durable pending queue in Correlation.

The two owner reads do not provide a distributed transaction or guarantee that
records cannot change or be purged after validation. Propose accepting committed
membership facts at validation time, with identity/source binding immutable for
that purpose; state transitions such as incident resolution are separate facts.
Owners must confirm those invariants and coordinate retention/deletion before
claiming stronger guarantees. Revalidate on a new attempt; do not implement a
permanent validation cache. This is not exactly-once delivery, a distributed FK,
or a solution to the snapshot/Recommendation commit gap.
Revocation between a successful check and local commit cannot be made atomic by
these queries. The contract must explicitly accept authorization at check time
within an agreed operation deadline, or specify stronger coordination separately;
this proposal does not promise instantaneous revocation across services.

#### Approval package, implementation order and acceptance

The requested decision is whether to adopt owner validation queries and the
proposed failure/consistency policy, accepting the architectural change as a
separate prerequisite. Contract authorities are Jhon Galvez and Ramiro Guzman;
the one-versus-both rule and authority for the architectural amendment must be
identified rather than assumed. Adoption requires a versioned contract and
identifiable approval, not merely merging this proposal.

The approval must also distinguish minimum committed existence/membership from
proof that Pipeline processed the exact upstream observation. Snapshot and
evidence messages have different IDs; the latter requires an explicit upstream
association and owner support. Neither deterministic IDs nor timestamp equality
provide that association. Recommend minimum owner-verified membership for this
decision, with the evidence source-item binding defined separately and explicitly
before claiming full provenance validation.

1. Approve/amend the architecture in its owning repository; authorize owners'
   implementation scope separately. Agree trusted machine-scope supplier,
   revocation cutoff and owner-approved evidence identity. Treat stronger
   historical source-item attestation as a separate requirement. Record any required model evolution
   there before migrations. Neighboring repositories are read-only in this task.
2. Pipeline and Log/Event implement and test their validation contracts over their
   own isolated stores, including missing, contradictory and unauthorized data.
   Agree PD-034 wire formats, limits/deadlines and retry exhaustion before runtime
   integration. Owner readiness cannot be replaced with permissive test adapters.
3. Correlation implements the validation application ports and internal adapters
   as part of the approved reception/persistence/handoff increment. Independently
   approved correlation rules may proceed separately; this proposal does not
   select the one-item unit or durable delivery receipt model.
4. Verify real local owner integration, then NLP and Recommendation contracts.
   Doubles can verify decision branches, but cannot prove reference existence or
   interoperability. Runtime identity/grants and deployment remain PD-035 inputs.

Acceptance must cover identical signals across different tenants/repos/runs or
attempts remaining separate; crossed incident/execution/evidence rejected;
valid cross-pipeline VERIFICATION links handled according to the agreed eligible
roles and authorization of both resources, without accidental pipeline equality;
missing-then-arriving data succeeding only after both owners confirm; outages
never becoming acceptance; denied scope revealing no reference existence; revoked
scope and ambiguous owner responses failing closed; forged/mismatched source-item
binding rejected if that stronger guarantee is selected; original provenance and
`correlation_id` preserved; no
snapshot or Recommendation call on validation failure. Duplicate delivery, lost
responses, concurrent requests and failed Recommendation handoff must also satisfy
the separately approved PD-010/034 recovery contract before end-to-end success is
claimed. Use synthetic fixtures and real isolated databases for owner assertions.

### Data-readiness assessment for the first correlation increment

**Read-only cross-service assessment, 2026-11-11; not contract approval.** The
user requested checking whether upstream data is wrong/insufficient and whether
the gaps actually matter to this service. The analyst inspected Ingestion and
Pipeline; the coordinator inspected NLP, Log/Event, Recommendation and the local
mapping; the independent reviewer challenged the conclusions. Source revisions
remain those above; the inspected neighboring checkouts were clean and unchanged.

**Conclusion:** no demonstrated identity/schema error requires replacing the
existing Correlation migration or the agreed Pipeline UUID rules. The evidence
v2 envelope carries enough structure to design a minimum context: operational
identity, attempt, source times, capture metadata, protected signals and
correlation. However, existence, authorization, stable replay and downstream
acceptance are not established by those fields. The current service has no
business receiver/write command, so these gaps block that future path, not the
already verified persistence foundation.

| Priority / gap | Evidence and concrete consequence | Smallest mitigation / actual gate |
|---|---|---|
| 1. Mutable NLP enrichment versus stable message replay | `ProcessEvidence.execute` reruns `DrainLogParser.parse`; the parser calls stateful `add_log_message`, and `CorrelationHttpDelivery._serialize` includes `template`. The same original message can therefore produce a different request after broker redelivery. Comparing all processed content could turn normal recovery into permanent conflict. | Agree stable source comparison separately from derived enrichment, and freeze/recover the accepted result. Changing source content under one key must still conflict. Do not merely ignore `template` while allowing the accepted snapshot to change. Blocks the receiver's durable replay semantics, not migration. |
| 2. No executable evidence owner or diagnostic receiver | Log/Event and Recommendation README files explicitly state documentation-only status. NLP computes evidence UUIDv5 from its message ID, but Log/Event has not adopted/implemented that rule. Current local PostgreSQL tests create no upstream owner rows and prove no delivery. A seven-field stored snapshot is not proof that Recommendation receives enough protected signal to diagnose; no evidence-fetch relationship is implied. | Agree evidence identity with its owner; implement/test evidence persistence and the minimum owner-validation contract before claiming reference validation. Agree required safe diagnostic information and Recommendation receipt separately under PD-010/034. Doubles permit local checks, not end-to-end claims. |
| 3. Missing references can be permanent operational outcomes | Pipeline's `ApplyExecutionSnapshotCommandHandler` can record `IGNORED_PAUSED`, reject `UNKNOWN_WORKFLOW` or require `LEGACY_ID_MAPPING_REQUIRED`. Ingestion broker publication does not wait for those consumer commits. UUID agreement does not ensure a row ever appears. | Preserve existing paused/registration rules. Distinguish demonstrated mismatch from unknown absence; agree a bounded recovery horizon and owned disposition when no row will be created. Do not invent missing rows or retry indefinitely. Blocks real processing of independently arriving events. |
| 4. Machine authorization across service boundaries remains unspecified | Ingestion's file authority is approved and implemented, but that does not grant NLP/Correlation access to every resource or expose a machine-scope query to other owners. Human access and capture scope are separate. | Reuse the approved authority choice; agree trusted scope propagation/verification, caller audiences/roles and revocation timing for the selected validation mechanism. Runtime operator/values remain activation inputs. No need to reselect Ingestion storage or automatically add tenant columns. |
| 5. Grouping decision identified by the assessment, now locally adopted | Current failed/timed-out evidence derives the automatic incident from the same execution ID. This supports that attempt's ORIGIN context; it does not identify an older incident for which another run is a VERIFICATION. | The user selected cumulative immutable multi-evidence revisions as recorded above; the domain rule is implemented. Durable group state/order remain pending. Multi-run remediation correlation needs its own association rule; the existing model's valid VERIFICATION links must remain valid. |

Sources supporting the assessment:

- [Ingestion transformer](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/blob/main/src/main/java/org/pipesor/ingestion/normalization/interfaces/messaging/transform/NormalizedMessagesTransformer.java)
  and [logical IDs](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/blob/main/src/main/java/org/pipesor/ingestion/normalization/domain/model/valueobjects/LogicalIds.java)
  preserve the shared operational tuple; snapshot and evidence have distinct
  message IDs. The capture ledger binds the two publications internally.
- [Pipeline snapshot handler](https://github.com/pipesor-org/pipesor-pipeline-service/blob/main/src/main/java/org/pipesor/pipeline/execution/application/commandhandlers/ApplyExecutionSnapshotCommandHandler.java)
  defines paused/stale/conflicting outcomes;
  [snapshot store](https://github.com/pipesor-org/pipesor-pipeline-service/blob/main/src/main/java/org/pipesor/pipeline/execution/infrastructure/persistence/jpa/SqlSnapshotStore.java)
  updates current execution `correlation_id` and stores exact version precision
  in its receipt. Historical evidence need not equal the latest projection.
- [NLP processing](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/src/pipesor_nlp_log_processor/application/process_evidence.py),
  [Drain adapter](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/src/pipesor_nlp_log_processor/infrastructure/drain_parser.py)
  and [serializer](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/src/pipesor_nlp_log_processor/infrastructure/correlation_http.py)
  establish the enrichment/replay interaction.
- [Log/Event status](https://github.com/pipesor-org/pipesor-log-event-service/blob/main/README.md),
  [Recommendation status](https://github.com/pipesor-org/pipesor-recommendation-service/blob/main/README.md)
  and [DD-014](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md)
  distinguish planned ownership/provenance from executable contracts.

Avoid manufacturing new prerequisites: missing tenant/capture fields in a
Pipeline snapshot are not a demonstrated defect of its globally identified
operational model. DD-014 preserves provenance through evidence IDs; it does not
require every owner table to duplicate the full capture envelope, nanoseconds or
producer message. Nor does minimum membership prove Pipeline consumed the exact
snapshot paired with an evidence message. Those stronger historical guarantees
require a separate justified contract; they are not universal blockers. Preserve
the original evidence `correlation_id` and source timestamps without requiring
equality with a mutable current execution watermark, status or correlation value.
NLP may further sanitize text, so byte equality with the stored excerpt is also
not an identity check. Optional metrics, branch and complete stage timings are
not required fields of the minimum DD-014 snapshot.

**Actual verification:** in a temporary isolated environment with the pinned
Drain3 0.9.11, the coordinator ran the existing NLP `ProcessEvidence` and HTTP
serializer on the synthetic v2 fixture. Process message A with excerpt
`Build failed for package alpha`, then a different message B with `beta`, then
reprocess A. Assertions confirmed identical message ID, evidence ID and sanitized
content, but `template` changed from `Build failed for package alpha` to
`Build failed for package <*>` and the serialized request changed. Source times
and tenant were preserved. HTTP retries inside one `deliver` reuse serialized
bytes; the demonstrated gap is reprocessing, not that retry loop. This is a
reproduced candidate-contract incompatibility, not a live Correlation failure.
No neighboring files, application dependencies or schemas were changed. Full
neighbor suites and live services were not run; no corrupt production data was
inspected or alleged.

The assessment initially prioritized the first slice and stable replay semantics;
their local adoption and remaining physical implementation are recorded above.
Next are the minimum owner identity/membership and machine-scope contracts, along
with durable group/receipt design and revision delivery semantics. Owner queries
remain one architectural option requiring approval, not a requirement established
by this assessment. Do not approve a broad schema expansion or full historical
proof merely to address minimum reference integrity.

### Decisions needed and responsible parties

Each row is a specific decision; none is approval by analysis or by this local
implementation. Jhon Galvez and Ramiro Guzman are the documented contractual
authority for PD-010/031/034/035; the one-versus-both approval rule is unspecified.

| Exact question / impact | Options and recommendation | Authority / dependent work |
|---|---|---|
| Accept NLP's v2 route/body/headers and the completion/recovery semantics above? Without this, broker completion can lose downstream work. | Local durable acceptance with deferred delivery, or synchronous confirmed delivery plus recoverable receipt. Recommend the latter to preserve the canonical synchronous flow. | PD-034, Jhon/Ramiro; NLP and Correlation owners coordinate. Blocks receiver, not owned-table foundation. |
| Where will scoped message identity, content comparison and delivery state live, with what physical constraints, precision and retention? | Evolve snapshot or separate owner receipt. Recommend a separate receipt if grouping is selected; first approve source-model evolution and canonical comparison. | PD-031/034/035 and model owner. Blocks durable receipt/acceptance; this task cannot modify docs repository. |
| How can Correlation verify authorized tenant/repository/run/attempt/incident/evidence membership, including not-yet-arrived data? | The [PD-031 proposal](#pd-031-decision-proposal-owner-verified-references) recommends owner validation queries after explicit architectural approval: the current graph cannot carry post-commit owner confirmations. Propose retryable unavailability for late/unknown references, without a new pending queue. Scope/provenance sources still require agreement. | PD-031 Jhon/Ramiro for contract; architecture authority must be identified for the new relationships. Pipeline and Log/Event own their implementations. No usable validation contract exists today. |
| Does Log/Event accept the UUIDv5 evidence-ID rule already computed by NLP? | Adopt the proposed rule or agree an owner-issued identity through the declared exchange. Recommend confirming the compatible rule with Log/Event; no ID changes here. | Log/Event owner, PD-031/032/033; no owner approval found. |
| Accept the proposed Recommendation request, minimal information, scope binding and durable response? | Self-contained safe snapshot versus a reference requiring an agreed retrieval relationship. Recommend a single self-contained submission; settle needed signals and idempotency first. | PD-010/034 Jhon/Ramiro with Recommendation owner. Blocks synchronous adapter/recovery tests. |
| What is the first correlation unit and what causes delivery/version closure? | Locally adopted by the user: cumulative immutable revisions by trusted scope, execution/attempt and automatic ORIGIN incident; each distinct verified evidence grows the set. No completeness/closure claim. The pure domain rule is implemented; durable state and revision delivery order remain to be specified. | Explicit session approval for this local rule; not blanket approval of the receiver, physical model or Recommendation contract. |
| What issuer/audiences/roles and trusted resource-scope evidence, payload limits and timeout/retry horizons apply? | Agree explicit fail-closed values versus keep integration disabled. Recommend explicit values before enabling REST; no permissive defaults. | PD-034/035 Jhon/Ramiro with security/backend/IaC owners; not settled by Entra as a technology choice. |

Concrete examples to decide the correlation rule: identical template/commit/time
across different tenants, repositories, runs or attempts must remain separate;
two distinct verified evidence members in the same approved group produce
cumulative immutable revisions; a repeated member does not grow the set. Receipt
recovery must separately distinguish matching input from conflicting reuse; the
group's membership check alone does not do that. A late distinct verified member
can extend the group without declaring earlier revisions complete. No timestamp
window, score, severity inference or close condition is implemented here.
