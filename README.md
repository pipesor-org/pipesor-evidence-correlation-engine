# pipesor-evidence-correlation-engine

Evidence Correlation Engine correlates protected evidence within workflow execution context, preserves provenance and delivers correlated incident context to the recommendation service. Its canonical architecture ID is `evidence_correlation_engine`; the planned runtime is Java / Spring Boot on Azure Container Apps.

The service runs as a Spring Boot web application (profiles `local` and `azure`)
that receives sanitized NLP output on `POST /internal/v1/processed-evidence`,
checks the operational reference with Pipeline and evidence membership with
Log/Event, persists the cumulative correlated snapshot in its owned `evidence`
tables, and returns success only after Recommendation confirms the context
(`POST /internal/v1/correlated-contexts`). Authentication follows the
[platform identity contract](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/contracts/platform-identity-v1.md)
(ADR-022): only the `nlp` workload with `Nlp.ProcessedEvidence.Send` may call the
intake, and Correlation signs its own workload tokens for its three peers. The
capture scope is bound per request from the evidence and confirmed by the owners;
the tenant must equal `PIPESOR_TENANT_ID`. Source locators follow
[protected evidence 2.2](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/contracts/protected-evidence-v2.2.md).

## Run

Configuration follows the
[runtime manifest](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/development/runtime-manifest.md).
Locally, start the shared stack from `pipesor-workspace` (`python3 tools/local_stack.py init && python3 tools/local_stack.py up`), then:

```bash
./mvnw -B -ntp -DskipTests package
set -a; . ~/.config/pipesor-local/env/correlation.env; set +a
java -jar target/pipesor-evidence-correlation-engine-0.0.1-SNAPSHOT.jar
curl -s http://127.0.0.1:8085/actuator/health/readiness
```

| Setting | Meaning |
|---|---|
| `PIPESOR_TENANT_ID`, `PIPESOR_WORKLOAD_ID=correlation` | environment tenant and workload identity |
| `PIPESOR_SIGNING_KEY_FILE` (local) / `PIPESOR_SIGNING_KEY_ID` (azure) | workload signing key |
| `PIPESOR_SECURITY_AUDIENCE` | `urn:pipesor:api:correlation` |
| `PIPESOR_SECURITY_TRUSTEDWORKLOADS_0_{ID,JWKSURI,ROLES}` | the `nlp` caller |
| `PIPESOR_ENDPOINTS_{PIPELINE,LOGEVENT,RECOMMENDATION}` | peer base URLs |
| `PIPESOR_DATABASE_URL/USERNAME/PASSWORD` | PostgreSQL; under `azure` the URL must carry `?sslmode=require` and authentication uses the workload identity |
| `pipesor.correlation.*` | intake and peer budgets (defaults in `application.yml`) |

Flyway migrates the owned tables at startup with its own history table
(`evidence.flyway_history_correlation`). Health: `/actuator/health/liveness` and
`/actuator/health/readiness`; public keys: `/.well-known/jwks.json`. The image is
built by `Dockerfile` from the packaged jar (uid 10001, port 8080); CI runs
`./mvnw verify`, builds the image and publishes it from `main`.

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

Verified on 2026-11-17 with Java 25.0.5.0.3 and Docker 29.9.3:

```bash
./mvnw verify
```

Sixty-six tests passed without failures, errors or skips: ten domain,
eight migration/JPA, fifteen durable-store, eighteen frozen-revision,
confirmation and capacity checks, four codec and eleven isolated HTTP checks. PostgreSQL 17.11 is
disposable via Testcontainers. They verify:

- Fresh V1/V2/V3/V4 application, reapplication and validation, exact owned tables and
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
- Frozen Unicode signals, changed-signal conflicts across messages/producers,
  concurrent aliases, deferred receipt failure and complete rollback.
  Historical reads recover exactly their own members, original incorporation
  traces and protected signals; crossed or damaged fixtures fail without partial
  output. V3 refuses populated V2, preserving data, schema, guards and history.
- Immutable historical scope, optional pipeline round-trip, crossed-resource
  rejection in both store and SQL, unchanged earlier scopes after new revisions,
  scoped alias concurrency and full rollback. V4 refuses populated V3 snapshots
  while preserving data, schema, guard and migration history.

The codec checks compare Java bytes and SHA-256 with the Python reference vectors,
preserve Unicode/control escaping and reject malformed projections/receipts. HTTP
checks use an actual loopback server fixture for receipt correspondence, explicit
recovery of the same request, redirects, caps, delayed headers/body and safe error
classification. They do not exercise Recommendation's database or real identity.
The client has no bean, endpoint/token defaults or retries; constructor bounds are
mandatory. Fixture bounds are not an approved operating policy, and token
acquisition/current authorization remain outside this adapter.

The internal confirmation handler covers already prepared revisions with an
authorized caller. Three PostgreSQL tests use a delivery double to exercise
unknown delivery, invalid receipts and recovery after a failed local
confirmation. They verify that pending state does not advance without a
matching receipt and that replay confirms the original revision.

Guarded preparation uses the actual canonical outbound byte count. Tests
cover an exact-fit revision, full rollback when a new revision exceeds the
supplied limit, and recovery of existing receipts after changing the limit.
The test capacities do not select an operating ceiling.

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
The [isolated NLP v2 decoder](docs/deployment-contract.md#local-v2-validation-and-input-fingerprint)
compares significant protected input using the producer's captured fixtures.
The separate outgoing request
codec is implemented and checked against reference vectors. Local technical
confirmation is not evidence of Recommendation reception. Tenant authorization,
foreign-reference existence, business timestamp nanosecond preservation, real
NLP delivery and Recommendation receipt persistence remain outside these checks.

The durable group/receipt schema follows integrated DD-023 in
[pipesor-docs](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md#DD-023),
merged through [model PR #13](https://github.com/pipesor-org/pipesor-docs/pull/13).
The signal extension is integrated through [service PR #6](https://github.com/pipesor-org/pipesor-evidence-correlation-engine/pull/6)
and [model PR #16](https://github.com/pipesor-org/pipesor-docs/pull/16). V3 adds
stage, protected summary and minimized protected content to each introducing
revision. V1/V2 remain unchanged. A populated V2 database is refused atomically
because the original protected signal cannot be recovered from fingerprints.
V4 similarly refuses existing V3 snapshots without an approved original-scope
mapping; opaque group keys cannot supply missing tenant or authorization history.
There is no purge, retention horizon, background retry or activation default.

The later [DD-024](https://github.com/pipesor-org/pipesor-docs/blob/main/model/domain_decisions.md#DD-024)
records an approved durable Recommendation receipt model. Its extension
integrated through [model PR #18](https://github.com/pipesor-org/pipesor-docs/pull/18)
adds a typed signal array. Correlation's isolated client and Recommendation's
local durable receiver passed a cross-repo test recovering the same committed
operation after a lost response. Operating budgets, overflow disposition and
current authority remain boundary work. See the
[current contract state](docs/deployment-contract.md#current-reconciliation-state--2026-11-16).

The approved HTTP mapping and comparison are reviewable in the
[candidate OpenAPI](docs/contracts/correlation-recommendation-v1.candidate.json)
and [synthetic vectors](docs/contracts/correlation-recommendation-v1.examples.json).
The artifact retains its candidate filename for traceability; activation remains
blocked on runtime policy and authority. With Python 3
and the existing local `jsonschema` package (verified with 4.19.2), run:

```bash
python3 tools/check_handoff_contract.py
```

This checks JSON schemas, request/header/receipt correspondence, exact signal
membership/origin structure and proposed comparison vectors using synthetic data.
The Python checker is neither complete OpenAPI validation nor an HTTP, authorization, persistence
or recovery integration test. It adds no application dependency; the isolated Java client separately uses
Boot-managed Jackson databind. Payload and
response budgets, cumulative overflow disposition, retry/deadline policy and
current authority remain explicit pending decisions in the candidate record.

### Retired merged-classpath harness

The former harness (`tools/local-handoff`, `tools/verify_local_*.sh`,
`tools/preliminary/run.py` and `external.py`) composed several services in one test
classpath. It was retired on 2026-11-22; its dated results in `docs/` remain
historical evidence reproducible from commit `25ad9c7`. End-to-end checks run the real
services against the shared local stack. Scenario manifests and fixtures under
`tools/preliminary/` are kept for the evaluation protocol.

## CI, images and disabled delivery

[CI](.github/workflows/ci.yml) validates Conventional Commit PR titles, squash messages and commit ranges with commitlint, and requires a nonempty PR body. Headers have a 72-character limit and lowercase scopes. Maintainers squash with the validated PR title. CI repeats commit validation on `main`, runs Maven verification through the shared Java action, and builds and checks the packaged image. Actions are pinned by full commit SHA.

Install Bun 1.4.4 and Lefthook 2.2.2, then run these checks from the repository root with Java 25, Python 3 and Docker available:

```bash
bun install --frozen-lockfile
lefthook validate
bun run setup:hooks
bun run commitlint --from origin/main --to HEAD
bun run test:release
python3 -m unittest discover -s tools -p 'test_*.py'
python3 -m unittest discover -s .github/delivery -p 'test_*.py'
./mvnw -B -ntp clean verify
revision="$(git rev-parse HEAD)"
image=pipesor-evidence-correlation-engine:local-ci
docker build --platform linux/amd64 --build-arg "VCS_REF=$revision" --tag "$image" .
GITHUB_SHA="$revision" python3 tools/check_image.py "$image"
```

Lefthook installs the same commitlint configuration used by CI on the local `commit-msg` hook. The image check verifies user `10001:10001` and the expected OCI source and revision labels. It starts an isolated container with networking disabled and requires a Spring Boot startup failure for missing platform configuration. The check removes its container on exit.

Only a successful push to `main` can publish. The shared workflow takes the exact tested image archive and checksum, verifies them and publishes that image to GHCR with `packages: write`. The technical tag is `ghcr.io/pipesor-org/pipesor-evidence-correlation-engine:sha-<commit>-<run>-<attempt>`. Use `ghcr.io/pipesor-org/pipesor-evidence-correlation-engine@sha256:<digest>` to identify a candidate for delivery. The schema-version-1 `candidate.json` records the image, digest, source SHA and original CI run/attempt. The transfer artifact lasts one day, test reports last 14 days, and the candidate manifest lasts 90 days. Use **Re-run all jobs** after a publication failure because the image tag and artifacts include the run attempt.

[Release candidate](.github/workflows/release.yml) is manual and hard-disabled. It accepts an original successful main CI run and attempt or a published release manifest, the exact image digest and a repository issue/PR recording authorization. Release versions name the existing digest. RC is optional. Supply an explicit `X.Y.Z` before a stable 1.x base. Stable requires retained successful preprod evidence for the same digest.

To plan locally with a retained candidate and its original CI attempt metadata:

```bash
GITHUB_REPOSITORY=pipesor-org/pipesor-evidence-correlation-engine bun .github/delivery/release.mjs plan \
  --candidate .delivery/candidate/candidate.json --run .delivery/candidate-run.json \
  --kind rc --version 0.1.0
```

Choose a version appropriate to the candidate and current Git tags. The plan writes local files only.

[Deploy dev](.github/workflows/deploy.yml) is manual and hard-disabled. It retains Pipeline's candidate, target and authorization inputs, including the earlier approved dev delivery and compatibility confirmation for rollback. The dev job has read-only GitHub access. Its Azure execution step has its own source guard pending separately authorized activation and authentication. Follow the [shared CI/CD guide](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/development/cicd.md) and [IaC ADR 004](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/adr-004-mvp-state-and-deployment.md) for activation prerequisites.

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
