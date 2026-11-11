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

Start Codex from this repository root so its [repository instructions](AGENTS.md) are discovered. Project configuration is in [.codex/config.toml](.codex/config.toml); [correlation_reviewer](.codex/agents/correlation-reviewer.toml) defines a focused reviewer. Review roles do not create separate sandbox permissions.

After changing instructions, start a fresh session and ask it to identify its active instruction sources. See the official [instruction-discovery guide](https://learn.chatgpt.com/docs/agent-configuration/agents-md) and [subagent guide](https://learn.chatgpt.com/docs/agent-configuration/subagents). A successful TOML parse alone does not establish live discovery or reviewer execution.
