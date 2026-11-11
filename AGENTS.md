# Repository instructions

## Context by task

- Before implementation, read the [common backend conventions](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/development/backend-conventions.md) and [Java/Spring conventions](https://github.com/pipesor-org/pipesor-docs/blob/main/docs/development/java-spring-conventions.md). Use the accessible pipesor-docs checkout when available, distinguishing local changes from integrated main. Apply only the conventions relevant to this service; they do not add tables, integrations or permissions.
- Use [README.md](README.md#development-and-verification) for setup, runnable commands and contribution policies. Add verified commands there when introducing application tooling; reference them rather than maintaining a second command list here.
- For correlation logic, input validation or diagnostic handoff changes, consult [architecture](docs/architecture.md#service-relationships) and its current source model, including correlation dimensions and constraints. Read the linked ADR-015 for diagnosis orchestration boundaries. Follow references for the affected subject, not the entire documentation set for every edit.
- For dependency selection, persistence, migrations or delivery work, consult the relevant sections of the [deployment contract](docs/deployment-contract.md). Its development baseline records the authorized version-selection policy; its data lifecycle records ownership and logical-reference requirements.
- Treat local architecture summaries as navigation, not frozen specifications. Use the accessible `../../pipesor-docs/` checkout or linked sources on `main`, accounting for later accepted decisions and relevant uncommitted changes. Inspect uncommitted source changes separately; their presence does not establish integration or approval. Report unavailable or conflicting evidence when it affects the task. Documentation links must follow `main` across repositories and relative paths within this repository; a consulted revision may be recorded as text only.

## Implementation constraints

- Preserve incident/execution identity, evidence provenance and `correlationId` through correlation, persistence and handoff. Do not merge unrelated executions because timestamps, commit IDs, errors or log templates match. Validate the context and logical references according to the agreed contracts; do not invent matching heuristics, time windows or missing evidence to resolve ambiguity.
- Confine writes to owned correlated context. Sharing the `evidence` schema does not authorize writes to `evidence_items`; preserve logical references to incident, execution and evidence without cross-context ORM navigation or cross-schema foreign keys. Do not create missing upstream records to satisfy persistence constraints.
- Keep the documented synchronous NLP-to-correlation-to-recommendation flow. Component projections do not create another deployment or require a second delivery of the same context. Do not introduce a direct broker consumer, public business API, RAG/LLM orchestration or diagnosis ownership to complete correlation.
- Check input validation, snapshot persistence and synchronous handoff together when changing behavior. Do not assume referenced data has already arrived, successful persistence proves downstream receipt, or retries are harmless. Use the agreed API, compatibility and failure-handling contracts rather than selecting retry or deduplication semantics by inference.
- Keep correlated summaries and diagnostic output context sanitized. Check authorization at the service boundary and avoid exposing evidence, credentials or sensitive correlation inputs through errors and logs; possession of an ID does not establish access.
- A request to implement a local increment authorizes its required local code, tooling and checks. Do not create cloud resources, permissions or secrets, or modify neighboring repositories as a side effect. Keep credentials and sensitive evidence out of committed files and reported output.
- If required correlation rules, reference-validation behavior or input/output contracts are unresolved, identify the missing agreement and any applicable pending decision. PD-010/034 cover the diagnostic reception/REST contracts; PD-031 covers logical-reference consistency. Continue independent work rather than inventing integration behavior or blocking the whole service over unrelated decisions.

## Verification and handoff

Use the applicable checks documented in the README. Add behavior checks with the first executable increment, including unrelated runs with similar signals, inconsistent or missing references, preserved provenance, sanitized snapshots and failed diagnostic handoff. Use synthetic evidence and isolated fixtures; live database or service integration checks need their own agreed scope and access.

For documentation-only edits, check affected links and source coherence; parse TOML when changing agent configuration. Do not claim a build or integration passed before it exists and has run. Keep the documentation that owns a changed behavior or command current in the same change. Report what changed, checks actually performed and remaining blockers. For substantial work spanning sessions, keep a task-specific plan with scope, acceptance criteria and verification progress; a small edit needs no plan file.

When a focused review is warranted and delegation is authorized, `correlation_reviewer` checks execution isolation, provenance, context ownership, logical references and synchronous handoff. Supply the task and affected paths. Its review supplements the main agent's verification and human review.

## Task-based agent workflow

This repository authorizes delegation within the user's existing task scope. For
substantial ambiguous requirements, roadmap questions or consequential contract
decisions, use `service_analyst` for bounded read-only research, built-in `worker`
for implementation and `correlation_reviewer` for independent review. Use
`explorer` for narrow code questions; implement small clear fixes directly with
proportionate verification. Do not run every role mechanically.

The coordinator owns scope, integration, conflicts, verification and completion.
Reuse the analyst's evidence; reread it when changed, disputed or insufficient.
Preserve applicable user approvals. Only unresolved decisions block dependent
work; continue specified and authorized independent work. Analysis and review do
not approve contracts, close pending decisions or expand publication/deployment
authorization. Neighboring repositories remain read-only unless separately authorized.

When appropriate: bounded analysis → select ready authorized work → worker →
independent review → fixes → coordinator verification and report. Keep at most two
open subagent threads, excluding the coordinator. Assign workers explicit file
ownership, relevant paths, acceptance criteria and checks; tell them they are not
alone and must preserve others' changes. Research/review roles do not edit or delegate.

Pass a compact feature brief containing:

- Goal, scope/exclusions, source paths/links, revisions and relevant local changes.
- Required behavior with approval status: integrated, explicitly approved locally,
  proposed, assumed/inferred or unresolved. Code or local presence is not approval.
- Observable acceptance and material failures; affected callers, contracts,
  persistence/configuration and dependencies.
- Each open decision's exact question, impact, options/trade-offs, sourced
  recommendation and documented authority or its absence; ordered ready/blocked work.

Workers return changed files, actual verification, deviations and blockers.
Reviewers independently inspect connected paths, source assumptions and whether
failure checks detect the stated risks; distinguish doubles from real integration.
Keep handoffs in messages and durable accepted decisions in existing owning docs.
Model/effort policy and discovery checks belong in [README](README.md#optional-codex-setup).
