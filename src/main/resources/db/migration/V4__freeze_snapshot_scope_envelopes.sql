-- Historical authorization scope cannot be recovered from opaque keys or fingerprints.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM evidence.correlated_contexts) THEN
        RAISE EXCEPTION 'Existing correlation history requires approved original scope migration mapping'
          USING ERRCODE = '23514';
    END IF;
END; $$;

ALTER TABLE evidence.correlated_contexts
    ADD COLUMN scope_tenant_id uuid NOT NULL,
    ADD COLUMN scope_github_installation_id text NOT NULL,
    ADD COLUMN scope_github_repository_id text NOT NULL,
    ADD COLUMN scope_pipeline_id uuid,
    ADD COLUMN scope_revision text NOT NULL,
    ADD CONSTRAINT correlated_contexts_scope_installation_ck CHECK (
        scope_github_installation_id ~ '^[1-9][0-9]{0,31}$'),
    ADD CONSTRAINT correlated_contexts_scope_repository_ck CHECK (
        scope_github_repository_id ~ '^[1-9][0-9]{0,31}$'),
    ADD CONSTRAINT correlated_contexts_scope_revision_ck CHECK (
        scope_revision ~ '^[A-Za-z0-9._:-]{1,128}$');

CREATE OR REPLACE FUNCTION evidence.guard_correlation_snapshot()RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, evidence AS $$
DECLARE g evidence.correlation_groups; previous evidence.correlated_contexts;
BEGIN
    IF NEW.id IS NULL OR NEW.group_id IS NULL OR NEW.incident_id IS NULL OR NEW.execution_id IS NULL
       OR NEW.evidence_ids IS NULL OR NEW.summary IS NULL OR NEW.correlation_id IS NULL OR NEW.created_at IS NULL
       OR NEW.signal_stage IS NULL OR NEW.signal_summary IS NULL OR NEW.signal_content IS NULL
       OR NEW.scope_tenant_id IS NULL OR NEW.scope_github_installation_id IS NULL
       OR NEW.scope_github_repository_id IS NULL OR NEW.scope_revision IS NULL
       THEN RETURN NEW; END IF;
    SELECT * INTO g FROM evidence.correlation_groups WHERE id = NEW.group_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Correlation group reference is unavailable'
          USING ERRCODE = '23503', CONSTRAINT = 'correlated_contexts_group_identity_fk';
    END IF;
    IF ROW(NEW.incident_id,NEW.execution_id) IS DISTINCT FROM ROW(g.incident_id,g.execution_id) THEN
        RAISE EXCEPTION 'Correlation snapshot identity does not match its group'
          USING ERRCODE = '23514', CONSTRAINT = 'correlated_contexts_identity_ck';
    END IF;
    IF COALESCE(array_ndims(NEW.evidence_ids), 0) <> 1 OR array_lower(NEW.evidence_ids, 1) <> 1
       OR cardinality(NEW.evidence_ids) < 1
       OR (SELECT count(DISTINCT member) FROM unnest(NEW.evidence_ids) member) <> cardinality(NEW.evidence_ids) THEN
        RAISE EXCEPTION 'Correlation snapshot requires a nonempty UUID set'
          USING ERRCODE = '23514', CONSTRAINT = 'correlated_contexts_evidence_set_ck';
    END IF;
    SELECT * INTO previous FROM evidence.correlated_contexts WHERE group_id = NEW.group_id
      ORDER BY cardinality(evidence_ids) DESC LIMIT 1;
    IF FOUND THEN
        IF ROW(NEW.scope_tenant_id,NEW.scope_github_installation_id,NEW.scope_github_repository_id,NEW.scope_pipeline_id)
           IS DISTINCT FROM ROW(previous.scope_tenant_id,previous.scope_github_installation_id,previous.scope_github_repository_id,previous.scope_pipeline_id) THEN
            RAISE EXCEPTION 'Correlation snapshot scope does not match its group'
              USING ERRCODE = '23514', CONSTRAINT = 'correlated_contexts_scope_binding_ck';
        END IF;
        IF g.confirmed_context_id IS DISTINCT FROM previous.id
           OR cardinality(NEW.evidence_ids) <> cardinality(previous.evidence_ids) + 1
           OR NOT NEW.evidence_ids @> previous.evidence_ids THEN
            RAISE EXCEPTION 'Correlation preparation requires the confirmed predecessor and one new member'
              USING ERRCODE = '23514', CONSTRAINT = 'correlated_contexts_chain_ck';
        END IF;
    ELSIF cardinality(NEW.evidence_ids) <> 1 OR g.confirmed_context_id IS NOT NULL THEN
        RAISE EXCEPTION 'Correlation first snapshot requires one member'
          USING ERRCODE = '23514', CONSTRAINT = 'correlated_contexts_chain_ck';
    END IF;
    RETURN NEW;
END; $$;
