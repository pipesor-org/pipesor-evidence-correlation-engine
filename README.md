# pipesor-evidence-correlation-engine

Evidence Correlation Engine correlates protected evidence within workflow execution context, preserves provenance and delivers correlated incident context to the recommendation service. Its canonical architecture ID is `evidence_correlation_engine`; the planned runtime is Java / Spring Boot on Azure Container Apps.

The local foundation contains a Java/Spring Boot Maven build, a non-web bootstrap,
the modeled `evidence.correlated_contexts` Flyway migration and its infrastructure
JPA mapping. It does not yet receive NLP requests, correlate incoming evidence or
deliver to Recommendation. Those parts require the
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

Verified on 2026-11-11 with Java 25.0.4.1 and Docker 29.9.3:

```bash
./mvnw verify
```

Five tests passed, without skips, against disposable PostgreSQL 17.11 via
Testcontainers. They check Flyway application/reapplication/validation, the exact
owned table and absence of foreign keys/foreign tables, Hibernate validation,
UUID-array/reference and UTC microsecond timestamp round-trips, coexistence of
distinct executions with identical summary/correlation, rollback, primary-key
conflict (`23505`) without overwrite and required-column rejection (`23502`).
The test starts the actual non-web Spring context with its isolated datasource.
It invokes Flyway explicitly with a test-only history in `evidence`; production
migration runner/history coordination remains PD-035. The containers are removed
after the checks. No live service or environment database is used.

The bootstrap has no HTTP listener, datasource credentials or connection defaults;
Flyway is disabled by default and Hibernate uses `ddl-auto=validate`. There is no
business startup command yet. The mapping is package-private infrastructure, not
an authorized input/write API or sanitizer. Tests use synthetic metadata only.
They do not validate tenant authorization, logical-reference existence, business
timestamp nanosecond preservation, message idempotency, real NLP fixture delivery
or Recommendation handoff. These remain dependent on the contracts above.

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

Setup verification on 2026-11-11 (codex-cli 0.167.1): TOML syntax, required role
fields, configured model/effort and two-thread limit passed. The prompt-input
diagnostic discovered both exact custom-role descriptions alongside worker and
explorer without a model invocation. It required execution outside the read-only
sandbox to initialize local CLI state; no global trust, provider or permission
configuration was changed. Live invocation of the new analyst is still unverified;
the earlier correlation reviewer execution is not an analyst pilot. No application
code changed in this agent-setup increment, so the backend suite was not repeated.
