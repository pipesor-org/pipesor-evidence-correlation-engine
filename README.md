# pipesor-evidence-correlation-engine

Evidence Correlation Engine correlates protected evidence within workflow execution context, preserves provenance and delivers correlated incident context to the recommendation service. Its canonical architecture ID is `evidence_correlation_engine`; the planned runtime is Java / Spring Boot on Azure Container Apps.

The local foundation contains a Java/Spring Boot Maven build, a non-web bootstrap,
the owned Flyway migrations and snapshot JPA mapping, a pure domain grouping rule
and an internal transactional store for cumulative snapshots, source receipts
and group confirmation. It does not yet receive NLP requests or deliver to
Recommendation. The business boundary and handoff require the
[contract decisions](docs/deployment-contract.md#local-contract-proposal-for-decision).
No container image or release automation is introduced.

## Documentation

- [Documentation baseline](docs/documentation-baseline.md): audited source revisions, evidence limits and adoption after merge.

- [Architecture](docs/architecture.md): service boundaries, integration context and current sources in `pipesor-docs`.
- [Deployment contract](docs/deployment-contract.md): development baseline, delivery requirements and unresolved contracts.
- [AGENTS.md](AGENTS.md): repository instructions for coding agents.

## Development and verification

The [development baseline](docs/deployment-contract.md#development-baseline) records
the dependency policy and compatibility sources. Use Java 25 and the committed
Maven Wrapper (Maven 3.10.0, Wrapper 3.3.4); Spring Boot 4.1.2 manages the dependency
set. Docker must be reachable for the PostgreSQL checks. The first run downloads
Maven/dependencies and the `postgres:17.11` test image.

Verified on 2026-11-16 with Java 25.0.5.0.3 and Docker 29.9.3:

```bash
./mvnw verify
```

Twenty-eight tests passed without failures, errors or skips: six pure domain,
seven migration/JPA and fifteen durable-store checks. PostgreSQL 17.11 is
disposable via Testcontainers. They verify:

- Fresh V1/V2 application, reapplication and validation, exact owned tables and
  three FKs solely between Correlation tables; Hibernate validates the snapshot.
- UUID/reference and UTC microsecond timestamp round-trips, defensive arrays,
  unchanged stored snapshots after detached merge, rollback and native
  required-column rejection (`23502`).
- Immutable cumulative memberships and restoration; separation by opaque scope,
  execution and incident; matching replay recovers the first frozen result and
  conflicting input changes nothing.
- Concurrent group/message creation, duplicate-member messages mapping to their
  original revision, pending-reception gating and monotonic confirmation.
  One test coordinates real JDBC queries to force a receipt-key collision
  (`23505`) and verifies complete rollback of the losing context.
- SQL rejection of invalid sets, broken chains, crossed memberships and ordinary
  history mutation. V2 refuses a populated V1 database and preserves its rows and
  original schema; it never fabricates scope or historical receipts.

The JPA test starts the actual non-web Spring context with an isolated datasource.
Tests invoke Flyway explicitly with test-only history; production migration
runner/history coordination remains PD-035. Containers are removed after checks.
No environment database or live service is used.

The bootstrap has no HTTP listener, datasource credentials or connection defaults;
Flyway is disabled by default and Hibernate uses `ddl-auto=validate`. There is no
business startup command yet. The mapping is package-private infrastructure, not
an authorized input/write API or sanitizer. The store is package-private and is
not registered as a business bean or listener. Callers must authenticate scope
and producer, validate owner references/ORIGIN membership and sanitize summaries.
Tests use synthetic metadata and supplied comparison fingerprints; no real
request comparator or canonicalization is implemented. Local technical
confirmation is not evidence of Recommendation reception. Tenant authorization,
foreign-reference existence, business timestamp nanosecond preservation, real
NLP delivery and Recommendation handoff remain outside these checks.

The durable schema follows locally adopted DD-023 in
[pipesor-docs](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md#DD-023),
reviewed separately in [model PR #13](https://github.com/pipesor-org/pipesor-docs/pull/13)
and not yet integrated into main. V1 remains unchanged.
There is no purge, retention horizon, background retry or activation default.

For documentation, check affected links and consistency with referenced sources,
then run:

```bash
git diff --check
```

For TOML configuration changes, run this syntax check with Python 3.11+ (the selected Python baseline supports `tomllib`):

```bash
python3 -c 'from pathlib import Path; import tomllib; [tomllib.loads(p.read_text()) for p in Path(".codex").rglob("*.toml")]; print("TOML syntax OK")'
```

Follow the organization [contribution policy](https://github.com/pipesor-org/.github/blob/main/CONTRIBUTING.md), [code of conduct](https://github.com/pipesor-org/.github/blob/main/CODE_OF_CONDUCT.md) and [security policy](https://github.com/pipesor-org/.github/blob/main/SECURITY.md). Licensing remains pending.

## Optional Codex setup

Start Codex from this trusted repository root. The
[task-based workflow](AGENTS.md#task-based-agent-workflow) follows the existing
Pipeline/Ingestion pattern: bounded analysis, authorized implementation and
independent review. Use the analyst when requirements or consequential decisions
need research, rather than for every small fix.

| Role | Model | Reasoning effort | Responsibility |
|---|---|---|---|
| Coordinator | gpt-6.1-sol | medium | Scope, integration, verification and completion. |
| [service_analyst](.codex/agents/service-analyst.toml) | gpt-6.1-sol | high | Reconcile sources/approvals, compare decision options and define observable acceptance. |
| Built-in worker/explorer | gpt-6.1-sol | high | Implement authorized work / answer narrow code questions. |
| [correlation_reviewer](.codex/agents/correlation-reviewer.toml) | gpt-6.1-sol | high | Independently check execution isolation, provenance, ownership and synchronous handoff. |

[Project configuration](.codex/config.toml) limits spawned threads to two,
excluding the coordinator. Custom roles pin their own model/effort. For bounded
worker/explorer tasks an explicit spawn may reduce effort to medium; use higher
effort only for concrete complexity. These settings follow Pipeline/Ingestion,
not a measured quality guarantee. Report any unavailable model or substitution.
Research/review instructions do not create a separate read-only sandbox or new
permissions; they inherit the parent's permissions. See the official
[subagent guide](https://learn.chatgpt.com/docs/agent-configuration/subagents).

Example request:

> Use service_analyst to compare the unresolved NLP receiver decisions with source
> revisions, existing approvals, options and recommendations. Implement only ready
> authorized work with worker; have correlation_reviewer independently challenge
> acceptance and failure checks. Preserve local changes and continue independent work.

After configuration changes, use a fresh session. Check TOML with the command in
[development verification](#development-and-verification), then inspect discovery
without invoking a model:

```bash
codex debug prompt-input 'List active repository instructions, specialist roles and authorization boundaries.'
```

Confirm the exact custom-role descriptions alongside worker/explorer; avoid
publishing raw diagnostic output, which may contain local context. TOML parsing
and prompt discovery do not certify live delegation. A live pilot must invoke
the actual `service_analyst` in a fresh session, then independently review its brief.
Do not label an explorer acting from copied instructions as that custom role.
See also the official [instruction-discovery guide](https://learn.chatgpt.com/docs/agent-configuration/agents-md).

Initial setup verification on 2026-11-11 (codex-cli 0.167.1): TOML syntax, required role
fields, configured model/effort and two-thread limit passed. The prompt-input
diagnostic discovered both exact custom-role descriptions alongside worker and
explorer without a model invocation. It required execution outside the read-only
sandbox to initialize local CLI state; no global trust, provider or permission
configuration was changed. A subsequent local continuation invoked the actual
`service_analyst` successfully: it reconciled integrated sources, identified the
PD-031 reference-validation decision and separated ready snapshot checks from
blocked business integration. This verifies a live analyst handoff in this session;
it does not certify every agent configuration or approve a contract. No application
code changed in the initial agent-setup increment, so its backend suite was not repeated.
