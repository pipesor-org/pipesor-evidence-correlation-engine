# MVP backend documentation baseline

Audit date: 2026-11-06. Scope: documentation and declarative agent instructions for `pipesor-evidence-correlation-engine`. This historical baseline was integrated in PR #3 at `5508c6dafe4ada970a7af03a04383e81d87dba71`; it neither approves executable delivery contracts nor certifies a deployed backend. The later foundation was integrated in [PR #4](https://github.com/pipesor-org/pipesor-evidence-correlation-engine/pull/4) at `4b8c226d3f88812f9ec42f7154d7fd468421041f`, revalidated against remote `main` on 2026-11-11. Current implementation and verification belong to [architecture](architecture.md#local-implementation-task--2026-11-11) and [README](../README.md#development-and-verification). The revisions below remain the historical audit inputs.

## Consulted revisions and authority

| Source | Exact integrated revision consulted | Scope |
|---|---|---|
| [pipesor-docs](https://github.com/pipesor-org/pipesor-docs/blob/main/AGENTS.md) | `c84ef3e4629fcb0c38315f32b641a5fb949e450d` | Architecture/domain models, proposed design, applicable accepted ADRs through ADR-019 and pending decisions. |
| [pipesor-iac](https://github.com/pipesor-org/pipesor-iac/blob/main/docs/mvp-resource-baseline.md) | `4b42c0c8e66578dfb890ae1cca8c9c31c774026e` | Integrated infrastructure preparation, native ingress, runtime identity lifecycle and dev-only readiness. |
| [Organization policy](https://github.com/pipesor-org/.github/blob/main/CONTRIBUTING.md) | `36691787bb9d0c9fd6b78c76bfe82adffa0c1c87` | Contribution rules and PR template. |
| This service, before consolidation | `47f258fef692d5aac23268ed79c12b50a73050e6` | Remote `main` baseline inspected before the documentation changes. |

Remote refs were fetched for reconciliation; these three source checkouts were clean and matched `origin/main`. Service changes were uncommitted working-tree candidates at audit start. Auxiliary local branches/worktrees, including the separate IaC bootstrap worktree record, were preserved and were not treated as integrated inputs. Documentation links follow `main`; SHAs in this record identify reproducible evidence, not navigation targets. Future changes require checking newer accepted decisions rather than silently treating this dated audit as current forever.

Authority remains by subject as described in [architecture](architecture.md). Model `confirmed` is not proof of implementation; proposed classes, DTOs and examples are not approved API contracts. Local source modifications require separate provenance and approval checks. The development toolchain direction in the [delivery record](deployment-contract.md#development-baseline) was explicitly authorized in the user session on this audit date; it supplements the source revision without closing all of PD-032.

## Reconciliation and remaining limits

The documents align with native ACA ingress, exclusion of workload Blob, managed Foundry and dev-only preparation. Architecture relationships and owned tables remain unchanged. Integrated GHCR, ACA identity and shared data/telemetry foundation choices are distinguished from pending application configuration, grants and deployment evidence. Reviewer guidance is contextual and references current evidence-storage boundaries.

Historical IaC text still describes the older docs overlay as unavailable; the consulted docs revision now contains ADR-019 and PD-033–036. That historical provenance limitation is not carried forward as a missing file or a closed contract. Earlier source-provenance discrepancies remain tracked by the [pending register](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/architecture/pending-decisions.md); this audit does not rewrite upstream historical records or approve missing evidence.

[Service-specific pending decisions](architecture.md#organization-conventions-consulted) remain scoped to the affected increment. Event/API/workflow-access contracts, data-reference consistency, migration execution and delivery readiness are not established by this baseline. AI-related increments also need approved model/dimension, provider compatibility and eligible corpus as applicable. Independent evidence recovery and retention remain unresolved. Local scaffolding and isolated tests may proceed within existing boundaries; integration and deployment require their own prerequisites.

## Verification and adoption

The historical audit checked the documented commands, local links/anchors, referenced source paths, model relationship IDs, owned-table assignments, agent TOML syntax and Git diffs. Application manifests, code and tests were absent at that audit revision; PR #4 subsequently added the local executable foundation. See [README](../README.md#development-and-verification) for current commands and actual application checks. Do not interpret a documentation check as application or cloud validation.

At the historical audit, the repository was private with `main` as default and squash-only merges enabled. Ruleset inspection returned HTTP 403 with a plan-upgrade requirement; human review and no direct pushes remain contribution rules, not certified server-side enforcement. Future changes require a dedicated branch and reviewed PR, with separate publication/merge authorization. Consumer agents must use integrated service revisions, not assume that another local worktree or open PR supplies an approved baseline.

No Azure authentication/resource tests, Terraform operations, application execution or live agent/reviewer-discovery tests were part of that documentation audit. The earlier Pipeline instruction experiment is not evidence that all seven agent configurations were exercised. That audit changed no infrastructure, application code or global configuration.
