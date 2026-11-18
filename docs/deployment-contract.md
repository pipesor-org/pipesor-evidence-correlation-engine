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

Integrated DD-023 adds `evidence.correlation_groups` and
`evidence.correlation_receipts`, owned solely by this service (Docs main
`174e7993984bed61e7d59b6139da204c1f6025aa`, including model PR #13).
The signal extension and V3 are integrated through model PR #16 and service
PR #6, respectively. Current consulted revisions are Docs
`60679a9a0bf45edd0ffb6b72a298433f7f4e1e7f` and Correlation
`2d2f7eff17c0abccf322796826d4a64af869e4dc`. Docs model 1.6.0 also contains
DD-024's Recommendation-owned receipt; that does not add Correlation tables or
prove that Recommendation has implemented reception.

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

**No deployment/runtime activation approval is recorded here.** DD-023 and
DD-024 have the scoped approvals described below; they do not approve live
integration or provisioning. ADRs retain their recorded Accepted status; missing
individual acceptance records remain PD-014. This document is insufficient to
claim deployability.

## Local contract proposal for decision

**Historical candidate, 2026-11-11, with later scoped approvals below.** Source
revisions and task scope are in
[architecture](architecture.md#local-implementation-task--2026-11-11). Read the
current state first; the historical proposals do not override later agreements
or close PD-010/031/034 as a whole.

### Current reconciliation state — 2026-11-16

| Subject | State and evidence | Remaining work |
|---|---|---|
| Grouping, serial revisions and source receipts | **Integrated:** DD-023 and Correlation V2; exact trusted scope + execution/attempt + ORIGIN incident, immutable cumulative membership, original-member receipts and monotonic confirmation. | Real authorization/reference checks and downstream confirmation remain boundary work; do not reselect the grouping or delivery order. |
| Minimal protected signal | **Integrated:** DD-023 supplement, model PR #16 and Correlation PR #6/V3. Each introducing snapshot freezes stage, protected summary and minimized content; historical reads recover exact membership. | Input protection, explicit payload budgets and transmission of this representation remain to be connected. No source timestamps or templates are added implicitly. |
| Recommendation receipt | **Integrated model and approved local wire mapping:** DD-024/model 1.7.0, operation equals context ID, atomic acceptance and matching duplicate/conflict. The reconstructed OpenAPI mapping/comparison was explicitly adopted by the user. | Recommendation has a local migration and isolated receiver implementation. Operational authority and budgets remain open. The original complete v1 definition was unavailable; the adopted reconstruction remains in force. |
| Receipt content versus frozen signals | **Integrated 1.7.0 includes signals:** the typed JSON array and exact-membership constraints now express DD-024's recoverable projection. | Recommendation's local V1 and receipt store were exercised with loopback HTTP and disposable PostgreSQL. |
| Structured resource scope | **Integrated persistence (PR #7):** V4 freezes five typed fields per snapshot and readRevision recovers the original envelope. The group key remains opaque. | The trusted supplier, current authorization/revocation and owner-reference contracts remain open. Stored historical scope is not an authority decision. |
| Owner references and evidence identity | **Still proposed/open:** PD-031 owner validation queries and Log/Event adoption of the evidence-ID rule have no usable contract in the consulted sources. | Settle owner-backed existence/membership and authorized scope, including late records, before accepting real requests. IDs and isolated fixtures do not establish existence or permission. |
| Runtime implementation | **Isolated Correlation client implemented:** complete projection, canonical codec and strict bounded HTTP with no activation bean. The local application service can deliver and confirm an already prepared revision. NLP reception and orchestration that starts with preparation remain pending. Recommendation has a local, explicitly composed durable receiver. | Settle operating capacity/overflow and current authority/reference contracts before workflow activation. Both adapters passed the isolated lost-response test. |

Consulted clean revisions: Correlation `2d2f7ef`, Docs `60679a9`,
Recommendation `ffa2dd3`, RAG `822cdf5`, NLP `1d6e54f` and Log/Event `d1f0277`.
The source lookup also inspected Docs PR #17/commit `60679a9`, its discussion and
reviews, Recommendation branches/PRs and the repositories' issues. PR #17 changes
the model and derived artifacts, not a complete wire DTO; no additional contract
definition was found. The user confirmed no copy is available. DD-024's approval
remains recorded; the exact missing definition is not treated as available.

The reconciliation recommendation is one self-contained submission per immutable
revision, preserving its exact protected signals and existing operation ID.
Recommendation would durably preserve the representation it accepts before
responding `accepted`. This is a proposal to complete the recoverable definition
from the recorded v1 agreement, not authorization to add JSON storage, another table or a retrieval
relationship. Keep the existing RAG client and receipt invariants.

#### Proposed reconciliation package

The following wire package remains **proposed, not an implemented DTO**. The
17/11 approval below settles its local persistence representations, while leaving
the boundary details open:

- One submission carries the seven original snapshot values (`id`, `incident_id`,
  `execution_id`, `evidence_ids`, operational `summary`, `correlation_id`,
  `created_at`) and exactly the signals returned by that revision's internal
  reader. Each signal preserves evidence ID, introducing context ID,
  incorporation correlation ID, stage, protected summary and minimized content.
  Use introduction order and verify the exact member set; no later signals,
  template or new source timestamps are inferred.
- The stable operation ID is the snapshot ID, as DD-024 already requires. No
  separate operation/revision identity is introduced. `accepted` or `duplicate`
  acknowledges the same context after durable commit, not a diagnosis.
- The structured scope carries DD-024's tenant, installation, repository,
  optional pipeline and scope revision. Its trusted supplier, verification and
  recovery must be defined; those values cannot be parsed from `scope_key`.
- Recommendation must preserve or recover everything the accepted representation
  requires after a restart. The subsequent approved model extension below
  selects five snapshot fields and a typed receipt array; it does not add a
  fetch endpoint or implement Recommendation's receiver.

| Remaining exact decision | Impact / recommended direction |
|---|---|
| Exact wire mapping and comparison | **Approved and implemented locally:** the OpenAPI mapping and Java codec use integrated fields and pass cross-language vectors. Recommendation must implement that mapping; do not reapprove the storage or codec choices. |
| Who supplies verified structured scope, how is it bound/recovered for the same operation, and how is current authorization reevaluated? | Require a verifiable source and recoverable immutable request binding; no defaults or parsing of the opaque key. Reauthorization must not silently replace historical scope/content. |
| Which operating size/deadline/retry limits and overflow disposition apply? | The significant projection and restricted comparison are adopted. The isolated adapter requires explicit mechanical bounds without defaults; actual runtime budgets remain unselected. RAG's 4000-character query limit is not the evidence budget. |

The projection, codec and isolated HTTP adapter are implemented. Workflow
activation, receiver persistence and real scope/reference acceptance depend on
their respective remaining operating/authority agreement or owner scope; the
whole service is not blocked by an unrelated pending item.
Acceptance must cover A then A+B with their exact signals and traces, response
loss after remote commit followed by duplicate, restart recovery of the same
operation/representation, conflict without effects, and no local confirmation
before a valid remote durable receipt. Invalid or unavailable authorized
references cannot become accepted snapshots or diagnostic calls.

The wire comparison codec/fingerprint must cover the agreed significant
representation deterministically. Recovery after a lost response or process
restart must resend the same operation and historical representation; authorization
must be checked again without silently rewriting that representation. The supplier
and recovery of the structured scope envelope, including its revision, are not
established by storing an opaque key or SHA-256 string.

`accepted` means durable receipt, not completed diagnosis or successful RAG/LLM
execution. It does not establish a worker, queue or eventual-diagnosis guarantee.
Recommendation owns construction of the RAG query, whose existing limit is 4000
characters. That query limit does not authorize truncating the accepted evidence
or converting the aggregate `summary` into concatenated logs.

Separate read-only finding for the Recommendation owner: its RAG client checks
the 15-second budget before HTTP but can return a later success without checking
again; a 14-second Retry-After followed by a 4-second response can return at
18 seconds. The fixed per-attempt HTTP timeouts do not use the remaining budget.
The coordinator reproduced this with a controlled test in a temporary copy of
Recommendation `ffa2dd3`: mocked HTTP returned `503` with Retry-After 14, then a
valid response while the test clock advanced four more seconds. The clock reached
18 seconds and the call returned success instead of throwing unavailability.
The regression assertion therefore failed as expected (one test, one failure,
zero errors/skips); this is evidence of the bug, not a passing suite or live HTTP
integration. No neighboring checkout is changed here. See the
[client](https://github.com/pipesor-org/pipesor-recommendation-service/blob/main/src/main/java/com/pipesor/recommendation/retrieval/infrastructure/RestRagKnowledgeRetriever.java)
and [configuration](https://github.com/pipesor-org/pipesor-recommendation-service/blob/main/src/main/java/com/pipesor/recommendation/configuration/RagClientConfiguration.java).

<a id="scope-and-signal-persistence-proposal--2026-11-17"></a>

### Scope and signal persistence — approved local increment, 2026-11-17

**Approved locally for model evolution and Correlation persistence, 2026-11-17.**
After the concrete five-field snapshot and typed receipt-array proposal, the user
said “Dale, sigamos”. This approves the physical increment below, not the
Recommendation implementation, wire contract or runtime authorization. This
refines the reconciliation above without reselecting grouping, operation IDs,
serial revisions or first accepted signals. No neighboring implementation is
modified. The original v1 definition remains unavailable.

#### Existing data and authority

| Required handoff fact | Existing source | What the source does not prove |
|---|---|---|
| Tenant, installation and historical scope revision | NLP v2 `capture_context.tenant_id`, `github_installation_id`, `scope_revision` | A JSON value or authenticated NLP caller does not establish resource permission or current authorization. |
| Repository | NLP v2 `pipeline.github_repository_id` | Existence, tenant/installation membership and permission still require owner-backed verification. |
| Pipeline | NLP v2 `pipeline_id` | Preserve the supplied logical ID after verification; do not regenerate it from repository text or fabricate an absent value. DD-024 permits null in its more general receiver model. |
| Current capture authorization | Ingestion's internal `CurrentMachineScope.verify(CaptureScope)` | It is not an exposed Correlation validation API. Its richer input also includes capture domain, App, account and workflow, so the smaller handoff envelope is not a substitute. |

Sources: [NLP handoff](https://github.com/pipesor-org/pipesor-nlp-log-processor/blob/main/docs/integration-contracts.md#3-correlation-handoff),
[Ingestion authority port](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/blob/main/src/main/java/org/pipesor/ingestion/capture/application/ports/CurrentMachineScope.java)
and [authority readiness](https://github.com/pipesor-org/pipesor-ingestion-normalization-service/blob/main/docs/deployment-contract.md).
Ingestion `1066785908ff4a7f7f20b448df7154138ab093e0` explicitly records that no
authorized scope configuration has been supplied. Its file-backed authority is
local implementation, not a cross-service trust or revocation contract. Reuse
the underlying authority agreement when available; do not copy a private file,
share owner SQL, expose a new endpoint or add a permissive allowlist by inference.

#### Why original-operation scope needs recovery

Before this extension, consider snapshot A accepted under scope revision 12. If the same group partition
remains applicable, an alias for the same evidence later arriving with revision
13 resolves to A's original snapshot,
as the original-member lookup requires. Building A's request using the alias's
scope would change that operation's fingerprint. A restart also cannot recover
revision 12 from an opaque group key or digest. Requiring the original NLP body
on every recovery would introduce an extra recovery prerequisite and does not
solve the alias case by itself.

**Approved Correlation model extension:** freeze five typed scope fields on
the introducing snapshot: `scope_tenant_id uuid`,
`scope_github_installation_id text`, `scope_github_repository_id text`,
`scope_pipeline_id uuid` and `scope_revision text`. Preserve DD-024's pipeline
optionality where applicable; do not discard a verified supplied pipeline ID.
All other scope values are required. Store them atomically with the existing
snapshot/receipt, and reject changes to that snapshot's envelope. These are
logical references with no cross-owner foreign keys. Do not replace or parse
`scope_key`, or change the approved partition identity.

This repeats a small envelope per revision intentionally: its authorization
revision belongs to the historical operation, not a mutable group head. Splitting
stable fields onto the group saves some repeated metadata but needs more joins
and a separate agreement about their lifetime; a complete raw request cache
copies more than the missing information. Five snapshot fields are the locally approved
typed option under the current opaque-key contract. Owning source evolution
precedes the new service migration; integrated V1–V3 remain unchanged.

The internal store treats the envelope as significant historical source input.
An existing message or evidence alias with a different original envelope,
including its scope revision, conflicts without effects even if a supplied
fingerprint is unchanged. A distinct new evidence member may use a different
scope revision while retaining the same stable resource tuple. The original
snapshot's envelope remains unchanged. Current reauthorization is not expressed
by replacing these historical input fields; no HTTP outcome or authority adapter
is selected by this internal consistency check.

Current authorization must be checked separately on every replay/read/delivery
that exposes protected data. A valid historical envelope alone is insufficient.
A changed or revoked binding must never be silently rewritten, accepted by
possession of the operation ID, or retried forever as an ordinary outage. Until
the authority contract defines these outcomes and disposition, real delivery
remains disabled. Persisting the envelope does not implement PD-031 validation.

#### Recommended Recommendation representation

The locally approved source-model extension adds one `signals jsonb` column to
the existing receipt, holding
only an ordered array of the already defined protected signal projection:
evidence ID, introducing context ID, incorporation correlation ID, stage,
protected summary and minimized content. This is a restricted, typed projection,
not arbitrary JSON, the original request or raw logs. The source-model projection
uses `evidence_id`, `origin_context_id`, `correlation_id`, `stage`, `summary` and
`content`. Its exact wire field names,
limits and validation must be agreed before implementation.

The recommendation avoids another receipt/operation table and preserves DD-024's
identity and immutable atomic acceptance. Application validation must require
exact membership relative to `evidence_ids`, no duplicates, valid stage/text,
original trace and deterministic introduction order; the source model must define
the applicable JSON shape/constraints. Scope, snapshot values and signals commit
before `accepted`. A matching replay returns `duplicate`; changed significant
content conflicts without effects. A hash cannot replace recoverable signals.

A normalized child table is an alternative when SQL queries or constraints on
individual signals justify it; none of the inspected Recommendation code does
that yet. It adds a table, rows and ownership constraints for the same reception.
Keeping only IDs/summary would instead need an agreed retrieval relationship or
an explicit reduction in diagnostic information. Neither is selected here.

#### Approved implementation scope

The user adopted the five typed snapshot scope fields and typed receipt signal
array above for local source-model evolution and Correlation persistence.
Correlation's extension follows DD-023, Recommendation's follows DD-024.
This approval does not choose wire details or authority relationships.
Neighboring Recommendation implementation still requires its own authorized
scope; current neighboring write authorization covers NLP and Docs.

The independently executable Correlation increment is
source-model evolution, a new migration, immutable mapping/store/read changes
and isolated PostgreSQL checks for frozen envelopes and alias/restart recovery.
Do not alter integrated V1–V3. Existing rows lacking verifiable original scope
need an approved mapping or atomic migration refusal; do not infer tenant or
revision from current upstream data. No listener, fake authority, credentials,
background worker or network integration is needed for this internal increment.
Recommendation's receiver work follows its owner scope and approved source model.

Required acceptance includes: A at revision 12 followed by alias A at revision
13 never changes A's stored envelope; a later distinct member does not alter an
earlier snapshot; restart reconstructs the original operation's scope and exact
signals; crossed stable resource identity is rejected according to the agreed
binding; failed receipt insertion leaves no partial envelope/signal; missing
legacy provenance causes atomic refusal. Real authorization/revocation tests
remain dependent on the authority contract, not replaceable by synthetic fixtures.

The remaining boundary decisions are the trusted authority supplier and its
current-versus-historical revision policy, owner-reference verification, exact
wire/canonical comparison and explicit payload/deadline budgets. This package
does not invent their values or claim that approving storage activates REST.

#### Verification of the approved local increment

Implemented against Docs `60679a9` plus local model 1.7.0 and Correlation
`2d2f7ef` plus V4. The owned snapshot now has sixteen fields, with optional scope
pipeline and fifteen required values. Scope, signal, snapshot and receipt commit
atomically. Contradictory original envelopes fail without effects; a later
member can advance scope revision within the same stable resource binding.

- `./mvnw verify`: 46 tests passed, zero failures/errors/skips, Java 25 and
  disposable PostgreSQL 17.11. Includes native SQL stable-binding rejection,
  optional pipeline, immutable merge, history reconstruction, concurrent aliases,
  full rollback and populated-V3 migration refusal preserving schema/data/guard/history.
- Source `make db` passed and generated DBML, SQL, ER and dictionary. Nine
  Correlation generation and 21 domain generation tests passed; seven Correlation
  PostgreSQL checks and one Recommendation projection check passed on disposable
  PostgreSQL 16.15/pgvector 0.8.7. The latter includes multi-member exact sets,
  duplicate signals/IDs, nulls, multidimensional arrays and append-only refusal.
- V1–V3 bytes remain unchanged. V4's snapshot guard matches the owning source.
  These checks do not constitute Recommendation's migration or runtime receipt.

No real scope authority, owner-reference query, NLP/Recommendation HTTP handoff,
wire codec, deployment or purge was exercised. The known inherited global Docs
RAG presentation/vector-fixture failures were not changed or rerun as part of
these focused checks. Model and service changes remain local and unpublished.

### Local remediation agreements and implementation

Following the assessment, the user explicitly adopted cumulative immutable
group revisions and separation of stable protected source from mutable NLP
enrichment. The grouping/storage agreements are now integrated under DD-023;
they are not closure of PD-010/031/034 as a whole. The user authorized local edits in Correlation,
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
real request comparator, listener or Recommendation adapter is enabled. The
minimal safe representation is already approved and integrated; the remaining
handoff reconciliation is recorded in the current-state section above.
The internal adapter assigns group and snapshot UUIDv4 values with
`UUID.randomUUID()` before insertion. Supplied message/evidence and operational
IDs are preserved; this does not change upstream identity algorithms. The receipt
uses its natural scoped message key and needs no extra UUID.

### Durable grouping proposal after the reuse audit

**Locally approved for implementation by the user, 2026-11-11.** After the reuse
audit and concrete proposal, the user explicitly approved this representation
and asked to continue to the next material decision. This adopts the two owned
technical tables, snapshot association, derived revision, shared confirmation
pointer and serial preparation policy. The resulting V2/source changes are now
integrated; this historical approval is not deployment approval or evidence of
implemented/live reception.
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

A fingerprint cannot recover discarded enrichment. The locally approved
representation below freezes a minimal protected signal at first incorporation.
DD-024 subsequently records approval of the versioned Recommendation contract
and durable receipt model. Its complete wire definition and signal preservation
must be reconciled as described above before enabling the business handoff.
No generic payload store is introduced.

The approved physical increment implements the representation and serial
policy, with conservative refusal of unmapped legacy rows and no purge/runtime
activation. A duplicate member under a different message is associated with the
original member revision; source comparison remains per scoped producer/message
key. Technical choices do not require reopening that approval. The next material
work for integration is completing the unavailable wire definition from the
recorded agreement and reconciling its recoverable representation; runtime identity, owner validation and live receipt integration remain separately
bounded work, not claims made by these storage checks.

The acceptance criteria cover concurrent first creation/member additions without
forks; matching replay preserving context ID/result; changed input leaving no
effect; rollback without partial receipt/snapshot; crash after preparation and
lost downstream confirmation; late acknowledgements without pointer regression;
multiple receipts sharing one revision; crossed scope/group/member rejection;
and migration/retention refusal when provenance or valid references would be
lost. The implemented storage subset and actual results are recorded in README;
these local checks do not establish real owner validation or downstream receipt.

<a id="next-decision-the-frozen-diagnostic-representation"></a>

#### Frozen diagnostic representation — approved and integrated

Approved on 2026-11-16 and integrated through model PR #16/service PR #6: freeze one minimal protected signal on the
snapshot that first introduces its evidence. This extends DD-023 explicitly;
Log/Event continues to own original evidence. Textual overlap is intentional:
Correlation owns the first accepted minimized processed projection needed for
repeatable delivery, without an evidence-fetch integration or full request copy.

- `signal_stage`, `signal_summary`, `signal_content` are required typed text.
  Stage reuses BUILD, TESTING, QUALITY_GATE, DEPLOYMENT, CONTAINER and
  INFRASTRUCTURE. `summary` remains the aggregate operational summary.
- The introducing evidence ID comes from distinct receipt membership for that
  context. Its snapshot `correlation_id` records incorporation trace; no new
  timestamps or claim of SQL-enforced canonical producer ordering is added.
- Matching replay preserves the original result. A new message for the same
  evidence returns its original revision only if the three signal values agree;
  contradictory signal values fail without effects. Templates are excluded.
- Revision R is reconstructed from same-group introducing snapshots through R
  in introduction order. Its distinct evidence IDs and count must exactly match
  R's membership; missing, duplicate or crossed projections fail closed. The
  internal reader checks expected scope/execution/incident, not user authority.
- V3 adds the fields and updates the snapshot guard without altering V1/V2.
  Any existing V2 snapshots make V3 refuse atomically: neither fingerprints nor
  current upstream text can reconstruct the originally accepted signal.

Callers must supply already protected and minimized text. Shape validation and
redacted diagnostic strings do not constitute a sanitizer. Input/output size
budgets, authorization/reference validation and completion of the missing
synchronous Recommendation request/receipt definition are required before exposing the business
path. The approved storage representation does not prove diagnostic quality or
live downstream receipt. No NLP storage/hash, generic payload, new table or RAG
client is needed for this increment.

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

The [producer-captured fixtures](contracts/nlp-processed-evidence-v2.examples.json)
record output from NLP `1d6e54f` using its actual `ProcessEvidence` and
`CorrelationHttpDelivery` with a synthetic processor and transport. The two
wire bodies were 1,167 and 1,144 UTF-8 bytes. Verified both nine-digit timestamps,
external IDs as strings, the 64-bit sequence value, matching identity headers,
nullable service/environment, and omission of `job` when absent. This checks
producer behavior and supplies cases for a future Correlation decoder. The
amount of data in the fixtures is insufficient for setting an operating ceiling.

The same capture exercise confirmed current NLP error classification:
`401/403` stop processing on authorization failure, `413` is permanent, and
`429/503` are transient. The processor and transport were synthetic; no live
HTTP, token provider, Broker settlement or sanitizer was exercised.

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

The original seven-column snapshot alone could not express this state. Integrated
V2/DD-023 now supplies the Correlation-owned receipt with scoped producer/message
key, source association, versioned input comparison and snapshot reference; the
group stores the shared delivery-confirmation pointer. V3 adds the three frozen
signal fields. Delivery state remains derived, not duplicated per receipt.
The physical model, atomicity and local concurrency/recovery checks are implemented;
the real comparison codec, safe boundary inputs, retention mechanism and remote
recovery remain bounded work. Do not store raw input
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

<a id="concrete-http-candidate--2026-11-17"></a>

### HTTP mapping and comparison — approved locally, 2026-11-17

The [candidate OpenAPI](contracts/correlation-recommendation-v1.candidate.json)
and [synthetic examples](contracts/correlation-recommendation-v1.examples.json)
make the reconstructed definition reviewable. After reviewing the concrete
candidate, the user explicitly adopted this mapping and comparison for an
isolated Java client (“Aprobado, seguimos”). The original complete v1 definition
remains unavailable; this approval supplies the missing concrete wire choices
rather than invalidating DD-024. Runtime activation and Recommendation's receiving
route are not authorized by this increment.

The candidate maps the existing snapshot ID to `operation_id`, its five historical
scope fields to `authorization_scope`, the original seven snapshot fields to
`context`, and the exact introducing signal projections to `signals`. It omits
group ID, a second revision counter, templates and new source timestamps. The
snapshot's original correlation ID supplies both context and HTTP correlation;
an alias's individual source trace remains in its source receipt.

Approved wire-only choices include the route/header/receipt mapping, strict
known-field compatibility, canonical lowercase UUID strings, six-digit UTC
microsecond snapshot timestamps, an explicitly present nullable pipeline and
lexically sorted evidence-ID strings. Sorting a wire copy does not mutate the
stored snapshot membership. Signal order remains incorporation order. Schema
validation cannot alone prove header equality, operation/context identity,
exact evidence membership, origin/last-snapshot correspondence or receipt
correspondence; the checker exercises
these extra invariants separately.

#### Candidate comparison and recovery

The downstream `request_fingerprint` covers the complete validated typed body,
including business correlation, historical scope and exact signals. It is
separate from the source message's stored `input_fingerprint`; those values
cannot substitute for each other. HTTP authorization and W3C tracing headers are
excluded. The approved restricted JSON encoding sorts the fixed ASCII property
names, emits compact UTF-8 without a BOM and preserves string values exactly:
no trimming, Unicode normalization or repeated sanitization. The candidate
artifacts define escaping/null handling and reference vectors. Reject duplicate
JSON keys and invalid Unicode before comparison; equivalent JSON whitespace or
object-key order does not create a new operation.

A receipt confirms only durable remote reception when version, status and both
IDs match the sent operation. A different ID/version, empty/malformed body,
unknown status or `202` does not permit local confirmation. Timeout, connection
loss and an unreadable success leave the result unknown: retain the same frozen
operation; a later permitted recovery sends that operation again. A retry does
not authorize another diagnosis ID or silently rebuild scope from current input.
No attempt count, backoff or retry horizon is selected by this candidate.

Distinct signal origin IDs and a last origin/trace matching the snapshot follow
from one introduced member per revision. The checker rejects contradictions in
these fields, without proving the existence or earlier relative order of origins.

The body is a protected, minimized representation supplied by an authorized
boundary; string/shape checks do not sanitize it. Current scope/revocation and
owner-reference verification remain mandatory before exposing data or duplicate
results. The Bearer scheme does not supply issuer, audience, role or principal
values. The future client must not automatically follow redirects with its
credential. Remote error bodies must not become unsafe local logs or responses.

#### Decisions and material failure acceptance

| Decision still needed | Concrete proposal / consequence |
|---|---|
| Mapping, strict compatibility, response and comparison encoding | **Approved locally:** use the versioned artifact and conformance vectors. The isolated Java client verifies actual byte/hash conformance; the Python reference check alone is not interoperability evidence. |
| Which bytes, field/member sizes, response size, total deadline and retry limits are supported? | No values are supported by the consulted producer contracts. Select them explicitly; the artifacts mark them pending rather than supplying runtime defaults. RAG query length and NLP local settings are not this contract's budgets. |
| What happens when cumulative growth exceeds the selected limits? | Validate the projected operation before preparing a new snapshot. Specify the upstream/operational disposition; do not truncate text, remove members, open another group, introduce windows or split a snapshot. A permanent excess must not become an endless transient retry. |
| Who verifies current scope, revocation and logical references? | Supply an agreed authority and failure/disposition contract. Historical scope and an authenticated service principal alone do not grant access to all resources. |

| Acceptance case | Required result | Evidence at this stage |
|---|---|---|
| A then A+B | Preserve each revision's scope, signals, IDs and incorporation traces; no later member in A. | Synthetic contract shape/invariant checks; persistence covered by the integrated 46-test increment. |
| Key-order/JSON whitespace changes | Same proposed request fingerprint; text/scope/significant signal changes differ. | Java bytes/hash matched Python vectors; no Recommendation codec exists to establish counterpart compatibility. |
| Crossed header, IDs, duplicate/missing members or unsafe unknown fields | Reject without confirming a different or partial operation. | Synthetic negative checks plus isolated codec/HTTP client checks. |
| Wrong/empty/non-JSON receipt or non-final status | No local `confirm()`. | Isolated client rejects these over actual loopback HTTP; no business workflow is activated. |
| Remote commit followed by lost response; concurrent duplicate; restart | Same operation recovers a valid `duplicate`, with one durable remote acceptance and no false local confirmation. | Future HTTP/PostgreSQL integration; not exercised by the candidate checker. |
| Revocation, references not yet committed or dependency outage | No unauthorized acceptance, disclosure or fabricated owner records. | Authority/owner contract and future real isolated integration remain required. |
| Capacity excess and deadline exhaustion | Agreed bounded failure/disposition; unchanged history and no new operation ID. | Numeric policy and overflow decision pending; not exercised. |

The command and actual check results are recorded in [README](../README.md#development-and-verification).
The earlier document/reference-check stage did not require an application rerun.
The now-approved isolated client increment runs the complete service suite and
records its actual results below; it does not implement the business workflow.

#### Isolated client construction and readiness

The client has no Spring bean, listener, automatic delivery task, token-acquisition
SDK or activation configuration. A caller supplies a current bearer token for
one invocation; acquiring it and verifying present resource authority are future
boundary responsibilities. Tokens do not enter stored projections, canonical
bytes or fingerprints. Errors must not reveal payloads, tokens or remote bodies.

Constructor bounds for request bytes, response bytes and elapsed time must be
positive and explicit. Their test values do not select runtime policy. No
implicit retry, redirect following or local `prepare()`/`confirm()` call is
introduced. The future orchestrator must validate the projected operation's
agreed capacity before preparation; a bounded client refusal does not settle
cumulative overflow disposition. A valid returned receipt alone does not activate
that orchestrator or prove Recommendation's persistence.

The existing query projection is extended instead of adding a near-duplicate DTO.
Its metadata comes from the immutable target snapshot under the same consistent
read as scope/signals. Alias traces remain individual source receipts; delivery
uses the target snapshot's original correlation.

Jackson databind is managed at 3.1.7 by the existing Spring Boot 4.1.2 parent.
Only the direct JSON dependency is added, without a web server starter, provider
SDK or new version override. JDK 25's bounded response body handler and HTTP
client implement transport; the non-web bootstrap and disabled Flyway remain.

#### Isolated client verification

`./mvnw verify` passed 61 checks on Java 25 with PostgreSQL 17.11 and a loopback
HTTP fixture: 46 existing domain/persistence checks, four canonical codec checks
and eleven HTTP checks. The coordinator inspected eight Surefire XML reports,
`BUILD SUCCESS`, and the packaged Jackson core/databind 3.1.7 libraries. The
Python mapping checker passed; V1–V4 bytes remain unchanged.

The HTTP fixture demonstrates actual request headers/body, strict accepted and
duplicate receipts, safe malformed/UTF-8/duplicate-key/trailing/unknown-field
rejection, response/request caps, redirects not followed, no hidden application
retry, delayed headers/body deadline handling and manual replay of identical
bytes after a lost response. It does not implement a database receipt or prove
remote durable commit. The persistence test separately confirms no delivery bean
is registered and that original snapshot metadata survives alias/revision reads.

Independent review found and closed a classification gap: a definitive rejection
with a huge/stalled error body must not turn into an unknown result merely while
waiting to buffer that body. The body handler now classifies status first and
propagates only a fresh safe failure kind. Tests cover 401/403/409 oversized and
stalled responses. Malformed success still means unknown reception. No material
findings remained in the final independent review.

No real token acquisition, scope authority, upstream reference validation,
Recommendation process/database, environment HTTP/TLS or deployment was tested.
No store confirmation or preparation workflow was introduced by the client.

#### Durable receiver and two-adapter verification — 2026-11-17

The Recommendation checkout has a local V1 for the DD-024 receipt, a strict JSON
decoder and an internal route factory requiring an explicit data source, byte
cap and authority implementation. It authorizes each submission before looking
for duplicates; the database transaction commits the snapshot, scope, signals
and fingerprint before replying. The default application composition leaves
the route inactive.

Recommendation's `./mvnw -B verify` passed 14 tests. In a separate local cross-repo
harness, the actual Correlation client sent the approved A+B fixture through an
HTTP proxy to the Recommendation route and PostgreSQL 17.11. The proxy discarded
the first response after observing `accepted`. A new client instance resent
identical bytes, and the committed operation returned `duplicate`. The harness
passed using synthetic authority and explicit fixture bounds. It did not exercise
environment credentials, actual resource authority, or the NLP workflow.

For the capacity decision, compare the encoded UTF-8 operation against the
agreed budget before writing a new unconfirmed snapshot. The reference bodies
measure 937 and 1,222 bytes and are synthetic examples, so size them against
representative cumulative evidence and the eventual ingress limits before
choosing an operating ceiling. Proposed disposition for a permanently oversized
new revision is an explicit rejection before persistence, with review through
the existing NLP permanent-rejection path if its inbound contract adopts it.
NLP already treats generic 4xx responses as permanent; forwarding this behavior
from Correlation still needs agreement, including recovery when receiver capacity
changes. Real authority, deployment, numeric budgets and reauthorization behavior
remain pending.

#### Local confirmation after durable receipt

For an already prepared revision, the internal application service sends the
frozen projection through the approved delivery port. It calls persistence
confirmation only after checking the receipt status and both IDs. Failed
delivery or mismatched receipt leaves the revision pending. If local confirmation
fails after remote acceptance, the caller can replay the same immutable
operation and use the remote duplicate receipt. Concurrent replays rely on
the existing downstream idempotency and monotonic local confirmation.

The caller must establish current authorization for the exact revision and
supply the access token. This service takes a prepared projection and makes
one attempt. It has no listener or activation configuration. It does not
choose which new contribution to prepare or set retry or capacity policy.

### Correlation → Recommendation proposal

The original 11/11 candidate proposed `POST /internal/v1/correlated-contexts`,
contract version `1.0`, an authorized scope envelope and seven snapshot fields.
It also proposed Entra Bearer authentication, `Idempotency-Key=operation_id`,
`X-Correlation-ID`, W3C trace context, `200 accepted|duplicate` and `409` for
conflict. These details remain historical candidate text, not a substitute for
the complete v1 contract whose approval DD-024 subsequently records. There is
no implemented receiving endpoint in the consulted Recommendation revision.

Preserve the approved receipt invariants: one operation per snapshot with
`operation_id=context_id`, atomic durable acceptance, matching replay without
another write and conflict without effects. Timeout leaves the outcome unknown;
recovery uses the same operation and representation, never a fresh diagnosis ID.
Correlation may confirm local delivery only after a valid remote receipt.

The seven-field candidate is not the full current Correlation representation:
DD-023/V3 also freezes stage, protected summary and minimized content per
evidence. Integrated model 1.7.0 now has the equivalent typed receipt signal representation.
Complete the missing wire definition and its acceptance decisions before implementing
the handoff. Do not treat source timestamps or templates as newly approved fields.
The trusted scope supplier/encoding and recoverability must also be explicit;
a tenant in a body or an opaque scope key is not authorization evidence.

The only documented business relationship remains the synchronous
`rel_correlation_recommendation`; its diagnostic-api component projection is not
a second delivery. Receipt storage/transaction and the receiver belong to
Recommendation. A receipt is not completed diagnosis, and no evidence-fetch API
or background processing guarantee is inferred. See the
[current state](#current-reconciliation-state--2026-11-16) for remaining work.

### Remaining work for NLP intake and activation

| Work | Proposed resolution and acceptance |
|---|---|
| Current machine scope and PD-031 | Use Ingestion's existing machine authority as the source for current capture permission, through an agreed machine operation on the Pipeline–Ingestion relationship. Pipeline validates committed pipeline, execution/attempt and ORIGIN incident membership. Log/Event validates committed evidence against that same authorized scope and execution/incident. Both validations must be bound to the incoming request. Confirm the architecture additions and agree how Log/Event verifies the scope before either owner exposes a validation operation. |
| Missing or denied references | Distinguish authorized but not yet visible, unavailable authority, contradiction and denial. Treat absence as retryable within an agreed horizon. Contradictions are permanent after authorization. Denials reveal no existence information. Rejected attempts create no snapshot or downstream delivery. The historical scope stays frozen when a replay is reauthorized. |
| Cumulative request capacity | Use the approved canonical UTF-8 encoder on the exact new revision inside the preparation transaction. A capacity failure must roll back the provisional writes, preserving the prior confirmed revision. Recover an existing operation with its original representation. Propose a terminal 413 for a new revision beyond the agreed ceiling. NLP already settles generic permanent 4xx by moving the message to its existing dead-letter path; agree the reason and operator disposition under PD-034 before mapping it. A pending revision made too large by a configuration change needs explicit recovery, with its original data preserved. |
| Deadline and retries | Set one Correlation processing deadline capped by the caller's attempt budget and current credential validity. Allocate remaining time across request reading, authority and owner queries, database preparation, delivery and confirmation. Include a read deadline at the receiver as well as a byte cap. Reuse scoped NLP redelivery for unknown delivery outcomes and preserve the operation ID. Agree numeric caps, attempt/deadline budgets and exhaustion handling from representative sanitized inputs and owner measurements before activation. |
| NLP receiver and confirmation | Use NLP's existing emitted v2 body as the contract candidate. Agree the exact compatibility, stable protected-input comparison, errors and handling of optional fields. Verify it with the real NLP serializer and exact source timestamps, then compose authorization, reference validation, bounded preparation and matching-receipt confirmation. Return NLP success only after local confirmation; an already confirmed replay also needs reauthorization. |

Current owner evidence: Pipeline `84f7ac0` has execution and incident lookups and
dashboard-facing reads; these do not implement the machine validation contract.
Log/Event `d1f0277` remains documentation-only and must implement evidence
persistence and adopt its identifier rule. Ingestion `1066785` has local
current-scope verification and needs an agreed cross-service machine operation.
NLP `1d6e54f` handles generic 4xx as permanent, 401/403 as authorization
failures, and 429/5xx as retryable. The 937- and 1,222-byte wire fixtures
check compatibility and cannot establish production capacity.

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

#### PD-031 contract proposal for owner review

The handoff baseline is now integrated through Correlation PR #8 and
Recommendation PR #6. This proposal covers new owner relationships and
authorization. It records neither owner approval nor new runtime grants.

| Operation | Proposed request binding | Proposed owner obligations |
|---|---|---|
| Correlation → Pipeline | Tenant, GitHub account/installation/repository/workflow, historical scope revision, pipeline ID, exact run/attempt, execution ID and ORIGIN incident ID. Authenticate the Correlation workload. Bind the request to the authenticated NLP producer context at Correlation. | Check current machine permission through Ingestion and verify committed pipeline, execution and ORIGIN membership against the same tuple. The human read API uses delegated user grants and cannot supply this contract. |
| Pipeline → Ingestion | Add a machine operation on the existing relationship, which currently covers verified user access. The lookup must cover the precise capture binding and resolve the GitHub App and capture domain from trusted configuration. | Call the existing current-scope mechanism for one unambiguous machine binding. The mechanism currently compares the full capture scope, including revision. Apply an agreed policy for replays under a different current revision. |
| Correlation → Log/Event | Authenticated Correlation workload, scope binding, execution ID, incident ID, evidence ID and source. | Verify that stored evidence exists and matches the committed binding for the scope and operation. The owner must implement evidence persistence and adopt the evidence identity rule first. Return minimal validation facts and no evidence content. |

**Recommended authority choice:** Explicitly delegate the evidence-validation
operation to Correlation for permitted resource scope. Correlation calls it only
after Pipeline/Ingestion confirms current permission for that attempt. Log/Event
must enforce a dedicated permission for the Correlation principal and reject
requests outside the agreed delegation. This design relies on Correlation's
preflight and requires that trust to be approved. Passing the original tenant
fields or a copy of a Pipeline response cannot by itself establish permission.
If Log/Event must check permission independently, approve an authority channel
or verifiable scoped assertion first. The two reference reads alone do not provide
independent authorization.

**Recommended replay rule for the first cut:** require the original capture
binding to remain authorized under the current authority's exact-revision
check. If authority explicitly reports that binding changed or was revoked,
deny disclosure or delivery and preserve the old operation unchanged. If the
authority cannot be queried, return temporary unavailability. This may hold older
pending revisions even if a new revision permits the same resource. Releasing
them requires an explicit reauthorization rule and operational disposition.
Never rewrite their historical scope or generate a replacement operation to
bypass the decision. A reauthorization request needs the full capture binding.
The five-field frozen downstream envelope alone omits account, workflow, App
and capture domain. The verified NLP replay and trusted owner configuration
must supply these facts. A future independent recovery entry point would need
an agreed way to recover the omitted facts before it can authorize delivery.

NLP currently pauses consumption on `401/403`. Treat denial of an individual
resource or stale binding as a distinct owner outcome at this stage. The NLP
wire contract must explicitly decide how to handle it without accidentally
pausing unrelated work, disclosing reference existence or retrying forever.

For recovery across unrelated authority revisions, the alternative is an explicit
current-permission lookup by stable resource identity with the observed
authority revision returned separately. That lookup requires a new owner
contract and a separate disposition for historical pending work.

Each owner response must bind its version and result to the validated request
and identifiers. Propose a per-attempt validation ID and the original
`correlation_id` for correlation. The validation ID has no persistence or
delivery identity semantics. Check the response within the shared deadline.
This establishes authority and committed membership at check time, with no
claim of an atomic revocation guarantee across the later commit. `confirmed` requires matching committed facts and authorization.
An absent committed row is `not_ready`; an outage, timeout or unrecognized reply
is `unavailable`; a committed contradiction after authorization is `mismatch`;
access refusal is `denied` without revealing existence. Use a shared deadline
for the validation attempt. No non-confirming outcome may persist a new
Correlation revision or initiate Recommendation delivery. Keep exact credentials,
timeouts, routes, response schemas and retry exhaustion in the owner-reviewed
contract. This proposal introduces no production defaults.

The decision for approval is whether to add the two owner reads, extend the
existing Pipeline → Ingestion relationship with the machine operation, and
adopt scoped delegation for Log/Event with the conservative replay rule above.
If independent Log/Event authorization is required, that part must be specified
before implementation. Pipeline may reuse existing owner queries. Log/Event's
storage and evidence identifier decision are separate prerequisites.

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
| 5. Grouping decision identified by the assessment, now locally adopted | Current failed/timed-out evidence derives the automatic incident from the same execution ID. This supports that attempt's ORIGIN context; it does not identify an older incident for which another run is a VERIFICATION. | The user selected cumulative immutable multi-evidence revisions as recorded above; the domain rule, durable group state and serial order are now implemented under DD-023. Multi-run remediation correlation needs its own association rule; the existing model's valid VERIFICATION links must remain valid. |

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

Rows distinguish settled scope from actual decisions still needed. Analysis and
local code do not extend existing approvals. Jhon Galvez and Ramiro Guzman are
the documented contractual authority for PD-010/031/034/035; the one-versus-both
approval rule is unspecified.

| Subject / exact question | Current state and recommendation | Authority / dependent work |
|---|---|---|
| Accept NLP's v2 route/body/headers and the completion/recovery semantics above? Without this, broker completion can lose downstream work. | Local durable acceptance with deferred delivery, or synchronous confirmed delivery plus recoverable receipt. Recommend the latter to preserve the canonical synchronous flow. | PD-034, Jhon/Ramiro; NLP and Correlation owners coordinate. Blocks receiver, not owned-table foundation. |
| Scoped message identity and durable state | **Integrated:** DD-023/V2 group and receipts, V3 minimal signals; the physical representation and serial order are settled. The real significant-input codec and receipt lifecycle remain open; do not redesign the tables to resolve them. | Existing user approvals and integrated model. PD-034/035 apply only to remaining codec, budgets and lifecycle; local Docs edits were separately authorized. |
| How can Correlation verify authorized tenant/repository/run/attempt/incident/evidence membership, including not-yet-arrived data? | The [PD-031 proposal](#pd-031-decision-proposal-owner-verified-references) recommends owner validation queries after explicit architectural approval: the current graph cannot carry post-commit owner confirmations. Propose retryable unavailability for late/unknown references, without a new pending queue. Scope/provenance sources still require agreement. | PD-031 Jhon/Ramiro for contract; architecture authority must be identified for the new relationships. Pipeline and Log/Event own their implementations. No usable validation contract exists today. |
| Does Log/Event accept the UUIDv5 evidence-ID rule already computed by NLP? | Adopt the proposed rule or agree an owner-issued identity through the declared exchange. Recommend confirming the compatible rule with Log/Event; no ID changes here. | Log/Event owner, PD-031/032/033; no owner approval found. |
| Reconstructed Recommendation wire mapping and comparison | **Adopted and implemented in the isolated client:** the OpenAPI, frozen fields and Java/Python vectors now define the previously missing concrete mapping. Do not reapprove grouping, storage, operation identity or the adopted codec. | PD-010/034 remaining work is Recommendation receiver/migration and proof of durable receipt; runtime authority, budgets and overflow disposition are separate pending inputs. |
| Correlation unit, revision order and closure | **Integrated:** exact scope + execution/attempt + ORIGIN grouping, each new verified evidence grows an immutable cumulative set, and preparation waits for prior confirmation. No completeness or closure claim. | Existing user approvals and DD-023; do not reopen. They do not approve unrelated receiver/security contracts. |
| What issuer/audiences/roles and trusted resource-scope evidence, payload limits and timeout/retry horizons apply? | Agree explicit fail-closed values versus keep integration disabled. Recommend explicit values before enabling REST; no permissive defaults. | PD-034/035 Jhon/Ramiro with security/backend/IaC owners; not settled by Entra as a technology choice. |

Acceptance examples for the settled correlation rule: identical template/commit/time
across different tenants, repositories, runs or attempts must remain separate;
two distinct verified evidence members in the same approved group produce
cumulative immutable revisions; a repeated member does not grow the set. Receipt
recovery must separately distinguish matching input from conflicting reuse; the
group's membership check alone does not do that. A late distinct verified member
can extend the group without declaring earlier revisions complete. No timestamp
window, score, severity inference or close condition is implemented here.
