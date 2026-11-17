# Architecture consumption

## Current handoff reconciliation — 2026-11-16

Current integrated state, checked after the historical work recorded below:

| Source | Consulted main revision | Implemented / approval boundary |
|---|---|---|
| Correlation | `2d2f7eff17c0abccf322796826d4a64af869e4dc` | PR #6 integrated V3, first protected signals and exact revision reconstruction; V2 group/receipt semantics remain integrated. No business receiver or remote handoff. |
| [Docs](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md) | `60679a9a0bf45edd0ffb6b72a298433f7f4e1e7f` | PR #16 integrated DD-023's signals; model 1.6.0/DD-024 subsequently records approved Recommendation receipt. The complete approved wire DTO was not located in consulted source files. |
| [Recommendation](https://github.com/pipesor-org/pipesor-recommendation-service/blob/main/README.md) | `ffa2dd3a34069ce226abaadb98420f1eef751d99` | RAG client/token/retries; no diagnostic receiver, receipt persistence or inference. Client completion is not completion of Recommendation. |
| [RAG](https://github.com/pipesor-org/pipesor-rag-retrieval/blob/main/README.md) | `822cdf51411b6ae0304ced1cbd0e7cb25d152080` | Retrieval API, knowledge persistence, embeddings/auth adapters and image automation. Live grants/corpus/deployment are separate activation requirements. |

The [owning reconciliation record](deployment-contract.md#current-reconciliation-state--2026-11-16)
distinguishes integrated rules, recorded approvals, missing contract material and
genuine open decisions. The older plans and verification counts below retain
their dated scope; they do not override this current state.

Task scope: reconcile the existing Correlation and Recommendation agreements
before implementing their boundary. Preserve grouping, exact identities, serial
revisions, first accepted signals and receipt invariants. No model change, new
wire contract, owner SQL, neighboring implementation, publication or deployment
is authorized by this documentation increment.

Plan and acceptance:

1. **Complete:** inspect both implementations and DD-023/024, preserving existing
   approvals. Update obsolete current-state claims; retain historical proposals
   as antecedents rather than active requests to reselect settled behavior.
2. **Complete lookup; definition unavailable:** source files, Docs PR #17/commit
   `60679a9`, discussions/reviews, Recommendation branches/PRs and issues yielded
   no complete wire DTO. The user has no copy. Preserve DD-024's recorded
   approval and use the explicit proposed reconciliation package in the owning
   contract to complete only missing definition, not invalidate the approval.
3. **Dependent:** specify only uncovered wire/codec/budget/recovery behavior.
   The same operation and historical representation must survive response loss
   and restart. Scope must be reauthorized without silently changing that
   representation; an opaque scope key cannot supply missing tenant/repository
   identities. Do not add a table or fetch API by inference.
4. **After contract readiness and owner authorization:** implement ready sender
   and receiver portions, then check isolated HTTP and durable PostgreSQL receipt
   together. Required cases include exact A then A+B signals, duplicate after
   lost committed response, conflict without effects, restart recovery, rejection
   of invalid scope/references and no local confirmation without a valid remote
   receipt. `accepted` does not prove diagnosis or successful RAG execution.

Verification evidence from the immediately preceding review: `./mvnw verify`
passed 38 tests with zero failures/errors/skips on disposable PostgreSQL 17.11;
eight Correlation source-generation tests passed against Docs `60679a9`.
The service reviewer found no material code defects in the implemented isolated
foundation. Those checks were not repeated for these documentation-only edits;
affected links, source coherence and whitespace are checked instead. No live
authorization, owner reference validation, NLP handoff or Recommendation receipt
was exercised. The Recommendation budget defect was subsequently reproduced with
mocked HTTP and a controlled clock in a temporary copy, not a neighboring checkout.
Its single regression assertion failed as expected when a success arrived at 18
seconds under the 15-second budget. This is bug evidence, not a passing suite or
live HTTP test; the owning reconciliation record carries the details.

### Scope and persistence implementation — 2026-11-17

The follow-up investigation found the five downstream scope values already in
NLP's candidate handoff. Ingestion has a current-scope port and local adapter,
but no cross-service validation contract; its documented authority configuration
is still unavailable. This is data availability with a separate trust gap, not
permission to trust forwarded fields or create owner queries.

The [concrete proposal](deployment-contract.md#scope-and-signal-persistence-proposal--2026-11-17)
specifies five immutable typed scope fields per Correlation snapshot and a typed
signal array on Recommendation's existing receipt. The user subsequently adopted
this local source-model and Correlation persistence increment with “Dale, sigamos”. The alias/restart case explains why historical scope cannot simply come
from whichever message triggers a retry. No approved group identity is changed.

Current implementation plan:

1. Evolve Docs model/generator constraints, DD-023/024 supplements and fixtures;
   regenerate derived files through their owning tools. No other owner behavior
   or RAG constraint changes.
2. Add an immutable scope value and snapshot mapping, transactional store binding
   and exact historical reads. Existing-message/member contradictions fail with
   no effects; a new member may have a new scope revision within the same stable
   resource binding. Current authorization remains a caller precondition.
3. Add V4 without changing V1–V3. Refuse populated legacy snapshots atomically
   unless original scope mapping is separately approved; never fabricate it.
4. Verify isolated PostgreSQL round-trip, null pipeline, conflict, rollback,
   concurrency and historical recovery, plus immutable migration bytes and
   source/service guard agreement. Independently review connected paths.

Implementation and verification completed locally on 2026-11-17. Service
`./mvnw verify` passed 46 tests with no failures, errors or skips on disposable
PostgreSQL 17.11. Source model 1.7.0 generation passed, as did 30 focused unit
checks and eight PostgreSQL 16.15/pgvector 0.8.7 checks. The coordinator inspected
Surefire XML/build output; V1–V3 are unchanged and V4's guard matches the source.
The independent reviewer checked isolation, original envelopes, exact signals,
rollback and migration refusal. A missing multi-member JSON-membership test was
added and passed before handoff; no material product finding remained.
Final coherence checks passed: 73 affected local links/anchors and whitespace
in both repositories. Neighboring NLP and Recommendation working trees remain
unchanged.
Real wire/authority readiness and Recommendation code scope remain separately
dependent. No HTTP activation, owner query, credentials, publication or deployment
is part of this increment. The owning contract records detailed acceptance and
verification limits.

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

### Local continuation after PR #4 — 2026-11-11

[PR #4](https://github.com/pipesor-org/pipesor-evidence-correlation-engine/pull/4)
is merged at `4b8c226d3f88812f9ec42f7154d7fd468421041f`. GitHub metadata and
remote `main` were revalidated; the initial local HEAD matched and the working
tree was clean. No local changes required recovery or removal. The PR discussion
and review lists were empty; merge is not separate contract approval evidence.
Docs remote `main` still matches `65792190d2ee32f0721d22848cd62cee1fd0d630`.
The live `service_analyst` inspected the clean neighboring source checkouts listed
above, which retain those revisions, and found no new receiving, reference or
handoff agreement. Neighboring repositories remain read-only.

The next required decision is PD-031: **how will Correlation verify authorized
tenant/capture, repository/workflow/run/attempt, incident and evidence membership,
including records that have not yet arrived?** A verifiable owner-backed proof
carried through the existing exchange is the recommendation to investigate;
new calls require an explicit architectural agreement. Neither option has an
approved mechanism. The documented contractual authorities are Jhon Galvez and
Ramiro Guzman; the one-versus-both rule remains unspecified. Pipeline and Log/Event
own their portions. See the existing [decision table](deployment-contract.md#decisions-needed-and-responsible-parties)
for options and the separate reception, correlation-unit, recovery and handoff
decisions. This analysis does not close them or reopen the integrated boundaries.

The subsequent user-requested [PD-031 proposal](deployment-contract.md#pd-031-decision-proposal-owner-verified-references)
examines that recommendation's feasibility: the existing graph has no path for
post-commit owner confirmations to reach NLP/Correlation. It therefore proposes
two owner validation queries, conditional on an explicit architectural amendment,
with trusted machine scope and source-item binding still to be agreed. This is
a proposal update, not approval of new relationships or a claim of implemented
reference validation. Documentation review checks source coherence, affected
links and whitespace; no application behavior changes in this proposal increment.
The analyst challenged feasibility against the existing graph and owner models.
Independent review identified an incorrect same-pipeline requirement for
VERIFICATION; the proposal was corrected to preserve accepted cross-pipeline
verification and require the applicable authorization for both resources.
All 32 local documentation links/anchors and `git diff --check` passed. Maven
was not repeated for this documentation-only increment; the seven-test result
below belongs to the preceding snapshot checks.

The subsequent [data-readiness assessment](deployment-contract.md#data-readiness-assessment-for-the-first-correlation-increment)
checks actual upstream code against the minimum need rather than assuming every
missing historical field is a defect. It corrects the proposal's stale statement
about Ingestion's already approved file authority, separates minimum membership
from stronger historical attestation, and records an isolated reproduction of
mutable NLP templates under broker reprocessing. No production code, neighbor
repository or accepted contract changes in this assessment.
The independent reviewer reported no material findings in the final assessment;
35 local documentation links/anchors and `git diff --check` passed. Neighboring
working trees remain clean. No application suite or live integration was rerun
for these documentation changes.

### Gap remediation: locally adopted scope and progress

User direction following the assessment authorizes local remediation in this
service and, where required for replay/contracts/model documentation, in NLP and
`pipesor-docs`. Publication, merge, deployment and changes to other neighboring
services remain outside this task. Existing changes are preserved.

Explicitly adopted in this session:

- Define multi-evidence grouping before enabling the business flow; the proposed
  one-independent-snapshot-per-evidence slice was not selected.
- Group by trusted machine scope, exact execution/attempt and automatic ORIGIN
  incident. Distinct verified evidence expands an immutable cumulative revision;
  replay of a member does not create a new revision. No time window, similarity
  matching, silence-based closure or assertion of complete run coverage.
- Separate stable protected source comparison from mutable NLP enrichment and
  retain the first accepted result for the same message. Changed source under
  the same scoped message ID must still conflict. NLP gains no durable store.

Implementation plan and acceptance:

1. Worker implements and tests only the grouping domain rule locally. Immutable
   memberships/revisions, deduplication of evidence membership and rejection of
   crossed scope/execution/incident must hold. The opaque scope is supplied by a
   future trusted boundary; constructing it proves no authorization or existence.
   Domain output is not yet a persisted snapshot or a delivery acknowledgement.
2. The overlap analysis does not justify a new NLP digest or wire field for the
   demonstrated template variation. Specify receiver comparison over existing
   significant protected fields, separate mutable `template`, and freeze/recover
   the accepted result. Proving original bytes hidden by sanitization is a
   stronger guarantee, not an implementation prerequisite. No NLP parser change,
   new cache or source hash is included in this increment.
3. Record approved local semantics in owning documentation without marking
   PD-010/031/034 globally closed or inventing schema fields. Group/head/receipt
   physical representation, per-source tracing into a group and Recommendation
   ordering/receipt behavior require a concrete agreement before persistence and
   handoff. Existing seven-column snapshots cannot provide those guarantees alone.
4. Independent review and applicable README checks; report source/behavior tests
   separately from live owner interoperability. Reference validation, scoped
   machine authorization and safe final disposition remain dependent work.

No receiving endpoint, business persistence command or migration is included in
the domain-only grouping increment. Worker implemented `CorrelationGroup` and
five behavior checks. `./mvnw verify` passed 12 tests (five domain and seven
isolated PostgreSQL), with zero failures, errors or skips. The coordinator
inspected both Surefire reports; independent review found no material findings
in the domain/tests and checked whitespace. No external validation, receipt or
revision delivery behavior is claimed. The local approvals were also recorded
in `pipesor-docs/docs/architecture/pending-decisions.md` with PDs still open;
no architecture/domain model or generated artifact was changed.

### Durable-state reuse audit and proposal

The next user request asked whether durable groups, revisions or receipts already
exist and whether the proposed data would duplicate upstream information. The
analyst and coordinator checked the integrated persistent model/decisions, local
mapping/migration/domain, Pipeline receipts, Ingestion ledger and Recommendation
version semantics; the local Docs pending-register overlay was inspected
separately and does not approve a physical model.

The [durable grouping proposal](deployment-contract.md#durable-grouping-proposal-after-the-reuse-audit)
reuses `correlated_contexts` for revisions and its evidence array for membership.
It proposes only a group identity/locking row, per-message receipts and a snapshot
association. Revision/head are derived; delivery confirmation is a distinct
group pointer so snapshots remain immutable. No revision/membership table,
separate operation UUID, NLP cache/hash or upstream-owner writes are proposed.
The receipt still needs the individual evidence reference and original message
trace; a cumulative snapshot or irreversible fingerprint cannot reconstruct them.

This is a reviewable proposal, not approval or implementation of schema, ordering,
private comparison encoding, downstream receipt or migration/backfill policy.
The recommended delivery gate and monotonic confirmation semantics remain for
decision. Snapshot reference growth, retention and the exact safe downstream
payload are explicit limits/gates. Independent review identified a stale earlier
receipt-state description; it now refers to the proposed group confirmation
pointer instead of per-receipt delivery state. No other material findings were
reported. Documentation checks passed: 40 local
links/anchors and `git diff --check`. No application tests or DDL were run for
this documentation-only proposal.

### Approved durable storage increment — implementation plan

The user subsequently approved the concrete reuse proposal and authorized work
through the next material decision. This approval now covers the two technical
tables, snapshot/group association, derived revision and group confirmation
pointer, with no next revision prepared until the previous reception is confirmed.
Do not reopen these choices as unresolved proposals.

Scope and acceptance:

1. Evolve Docs' persistent source and DD-023 locally, preserving its existing
   pending-register overlay and unrelated questions. Regenerate artifacts through
   its owning Makefile; do not edit generated output. The two approved tables
   extend the approved persistent/ER count from 25 to 27, following the DD-021
   precedent, without changing architecture view budgets/memberships.
2. Add V2 without changing V1. Reject an existing V1 dataset whose missing scope
   and source receipt cannot be established, before any destructive change; prove
   atomic refusal on an isolated legacy fixture. No live migration is authorized.
3. Update the internal mapping and implement transactional owned-store preparation,
   matching/conflicting receipts, original-member replay and monotonic confirmation.
   No SQL transaction spans external I/O. No public input boundary, reference
   client, Recommendation adapter, new NLP hash or generic payload store is added.
4. Verify fresh/reapplied schema, exact ownership and own-context constraints,
   immutable snapshots/receipts, crossed associations, cumulative chain, rollback,
   concurrent creation/preparation and pending-delivery gating on PostgreSQL.
   Confirmations in these tests are synthetic technical operations, not HTTP
   downstream receipts. Source comparison tests using synthetic fingerprints do
   not certify a future real request comparator.
5. Independent review, fixes and README checks. Record actual results and source
   provenance; no publication, merge, cloud resource or deployment action.

The diagnostic representation decision below was subsequently approved locally.
The historical verification records in this section retain their original
baseline; current signal work is recorded after PR preparation. Numeric runtime
horizons, permissions and retention activation are not selected by fixtures.

Implementation completed locally and verified on 2026-11-16:

- Docs source is model 1.4.0/DD-023 on top of integrated `6579219`, preserving
  its prior local pending-register changes. `make all` completed and regenerated
  the model-derived DBML, SQL, ER and dictionary through the owning tools; no
  generated file was hand-edited. Existing C4 warnings about unspecified source
  descriptions/technologies remained warnings; no architecture fact was invented.
- `make test`: 116 checks passed. `make test-postgres`: 19 checks passed against
  disposable PostgreSQL 16.15/pgvector 0.8.7, the Docs tool's existing image.
- Correlation `./mvnw verify`: 28 checks passed with zero failures, errors or
  skips on Java 25.0.5.0.3 and disposable PostgreSQL 17.11. The coordinator
  inspected final Surefire reports: six domain, seven migration/JPA and fifteen
  durable-store tests. After the final index change the complete source/store
  checks were rerun successfully. One direct Maven attempt was blocked by the
  sandbox's `~/.m2` lock access; the authorized rerun passed.
- The independent reviewer found a missing receipt lookup index. It was added
  to the source extras and V2, with a generator check. The source guard block and
  V2 match exactly; V1 has no diff. A deterministic concurrency test coordinates
  real JDBC reads, observes receipt-key `23505` and verifies rollback of all
  losing effects. No production test hook or fabricated database result is used.
- Snapshot restoration is linear before adding a member, and JDBC arrays are
  released. Receipts preserve the original member revision even when several
  messages refer to it. Confirmation never regresses or changes timestamps for
  an old/duplicate acknowledgement. No material review findings remain in the
  code/constraints; documentation was updated to distinguish these results from
  the earlier twelve-test domain increment.

All data are synthetic. The fingerprint format is stored/compared but the actual
request projection/codec is not implemented; tests supply comparison values.
Confirmations are local technical acknowledgements, not actual Recommendation
receipts. No business listener, owner clients, runtime credentials, automatic
migrations, purge, image publication or deployment was introduced. Work now
reaches the [diagnostic representation decision](deployment-contract.md#next-decision-the-frozen-diagnostic-representation).
Final documentation coherence checks passed: 41 service-local links/anchors,
the two added source-doc local references, whitespace checks in both repositories
and an unchanged V1 migration. Existing unrelated changes were preserved; all
work remained local and uncommitted before the publication preparation below.

### PR preparation — 2026-11-16

Remote Docs main advanced to `14a71dc44855d60236bdfdeb2d953a2e3edc8de6`, which
already assigns DD-022 to RAG corpus lifecycle. The unpublished Correlation
decision was renumbered to DD-023 and rebased onto that accepted main without
changing its approved behavior. RAG entities and open questions were checked
against the new main and preserved exactly. The generated model and PostgreSQL
fixture were reconciled with RAG's required content hash; no RAG runtime or
contract change is introduced by this increment.

The companion [model PR #13](https://github.com/pipesor-org/pipesor-docs/pull/13)
publishes that evolution from source head `849414d6b67b27ecb18cb06e2a2bc5fd2e20bad7`.
That PR and service PR #5 subsequently merged; the integrated baselines at that point
are Docs `174e7993984bed61e7d59b6139da204c1f6025aa` and Correlation
`5034a852eaf94cfabb650e1b598565ca6d40863f`.
`make all`, the 116 model tests and 19 isolated PostgreSQL checks passed again
after reconciliation; the service's 28 checks also passed with the renamed
source reference. Focused independent review found no material reconciliation
findings. Publication of these PRs does not authorize merge, deployment or the
next diagnostic-payload increment.

### Frozen protected signals — approved local increment, 2026-11-16

Goal: recover the exact protected signals behind an immutable cumulative
revision without requiring a new upstream fetch. The user approved one minimal
stage/summary/content projection per introducing snapshot, including DD-023's
explicit narrow exception for owned processed text. Grouping, identity, serial
preparation, original-member receipts and synchronous orchestration are retained.
The [owning representation contract](deployment-contract.md#next-decision-the-frozen-diagnostic-representation)
describes fields, provenance and remaining boundary requirements.

Scope and acceptance:

1. Evolve the owning model to 1.5.0, generate DBML/SQL/ER/dictionary and add V3;
   preserve integrated V1/V2 and refuse legacy rows without recoverable signals.
2. Freeze the introduced signal atomically with snapshot/receipt. Replay returns
   the first result; aliases agree on signal or fail with no effects.
3. Reconstruct an older revision in deterministic introduction order, checking
   exact membership and expected scope/execution/incident; reject partial output.
4. Verify migrations, immutable mapping, rollback, concurrency and failure cases
   against disposable PostgreSQL, then independently review connected paths.

Verification progress: source generation, eight Correlation generator tests and
six isolated PostgreSQL 16.15/pgvector 0.8.7 Correlation checks passed. The current
integrated Docs baseline independently reproduces nine global suite failures
(three failures/six errors from missing RAG presentation labels) and one global
PostgreSQL failure (old three-dimensional vector fixture versus the approved
1536-dimensional constraint). These unrelated failures are not changed or
reported as passing. `make test` in the actual changed source reproduced the
same 3 failures/6 errors (117 total, 108 passed).

Service `./mvnw verify` passed 38 checks with no failures, errors or skips:
eight domain, eight migration/JPA, fifteen store and seven frozen-signal tests
on disposable PostgreSQL 17.11. The coordinator inspected final Surefire XML and
build output. V1/V2 are byte-identical to HEAD; the V3 guard matches the owning
source helper. Sixty-eight affected local links/anchors and whitespace checks
in both repositories passed. Independent review found no material product
findings; a malformed-data test fixture was corrected before the final run.
No real owner validation, NLP delivery, Recommendation receipt or public business
route was introduced. This was the local verification record before publication;
service PR #6 and model PR #16 subsequently merged. No deployment is evidenced.

Independent scope of the earlier continuation: worker added checks of evidence-array ownership and attempts
to merge a changed representation into the existing immutable JPA snapshot.
Acceptance is preservation of every original field after commit and a fresh
EntityManager read. This checks the infrastructure mapping, not SQL-level
immutability, business idempotency or reference authorization. No production
behavior, schema, dependency or caller changes are planned. The coordinator
updates the existing documentation and the independent `correlation_reviewer`
checks the connected mapping, tests and source assumptions before handoff.

Verification at continuation start: `./mvnw verify` passed all five existing
PostgreSQL tests without failures, errors or skips. After the two added checks,
worker ran the same command: BUILD SUCCESS, seven tests, zero failures, errors
or skips against disposable PostgreSQL 17.11. The coordinator inspected the final
Surefire report. Maven emitted HTTP 401 warnings for metadata from secondary
repositories; they did not prevent the build. The 27 local documentation
links/anchors and `git diff --check` passed. The live `correlation_reviewer`
independently inspected the diff, connected mapping/migration/configuration,
callers and owning sources, checked the seven-test Surefire result and ran
`git diff --check`; it reported no material findings. It did not repeat Maven or
test live services. These checks do not establish SQL-level immutability,
authorization, business idempotency or downstream reception.
No publication, merge or deployment is authorized.

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
