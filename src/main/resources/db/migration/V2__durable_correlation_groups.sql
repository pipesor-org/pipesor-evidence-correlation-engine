-- V1 has no trusted scope, producer or receipt provenance from which to backfill.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM evidence.correlated_contexts) THEN
        RAISE EXCEPTION 'Existing correlation history requires an approved migration mapping'
          USING ERRCODE = '23514';
    END IF;
END; $$;

CREATE TABLE evidence.correlation_groups (
    id uuid PRIMARY KEY,
    scope_key text NOT NULL,
    execution_id uuid NOT NULL,
    incident_id uuid NOT NULL,
    confirmed_context_id uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT correlation_groups_partition_uq UNIQUE (scope_key, execution_id, incident_id),
    CONSTRAINT correlation_groups_identity_uq UNIQUE (id, incident_id, execution_id),
    CONSTRAINT correlation_groups_scope_ck CHECK (btrim(scope_key) <> '' AND scope_key ~ '[^[:space:]]'),
    CONSTRAINT correlation_groups_times_ck CHECK (updated_at >= created_at)
);

ALTER TABLE evidence.correlated_contexts ADD COLUMN group_id uuid NOT NULL;
ALTER TABLE evidence.correlated_contexts
    ADD CONSTRAINT correlated_contexts_group_id_uq UNIQUE (group_id, id),
    ADD CONSTRAINT correlated_contexts_evidence_shape_ck CHECK (
        array_ndims(evidence_ids) = 1 AND array_lower(evidence_ids, 1) = 1 AND cardinality(evidence_ids) > 0),
    ADD CONSTRAINT correlated_contexts_group_identity_fk FOREIGN KEY (group_id, incident_id, execution_id)
        REFERENCES evidence.correlation_groups (id, incident_id, execution_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE evidence.correlation_groups
    ADD CONSTRAINT correlation_groups_confirmed_context_fk FOREIGN KEY (id, confirmed_context_id)
        REFERENCES evidence.correlated_contexts (group_id, id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE evidence.correlation_receipts (
    scope_key text NOT NULL,
    producer_key text NOT NULL,
    message_id uuid NOT NULL,
    evidence_id uuid NOT NULL,
    correlation_id text NOT NULL,
    context_id uuid NOT NULL REFERENCES evidence.correlated_contexts (id),
    input_fingerprint text NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (scope_key, producer_key, message_id),
    CONSTRAINT correlation_receipts_scope_ck CHECK (btrim(scope_key) <> '' AND scope_key ~ '[^[:space:]]'),
    CONSTRAINT correlation_receipts_producer_ck CHECK (btrim(producer_key) <> '' AND producer_key ~ '[^[:space:]]'),
    CONSTRAINT correlation_receipts_correlation_ck CHECK (btrim(correlation_id) <> '' AND correlation_id ~ '[^[:space:]]'),
    CONSTRAINT correlation_receipts_fingerprint_ck CHECK (input_fingerprint ~ '^v1:sha256:[0-9a-f]{64}$')
);

-- DD-023: membership cardinality is the revision; no second counter or operation ID.
CREATE UNIQUE INDEX correlated_contexts_group_revision_uq
ON evidence.correlated_contexts (group_id, cardinality(evidence_ids));

CREATE INDEX correlation_receipts_context_evidence_idx
ON evidence.correlation_receipts (context_id, evidence_id);

CREATE FUNCTION evidence.reject_correlation_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
    RAISE EXCEPTION 'Correlation history mutation requires an approved retention operation'
      USING ERRCODE = '23514', CONSTRAINT = 'correlation_immutable_ck';
END; $$;

CREATE FUNCTION evidence.guard_correlation_group() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, evidence AS $$
DECLARE next_revision integer; previous_revision integer;
BEGIN
    IF NEW.id IS NULL OR NEW.scope_key IS NULL OR NEW.execution_id IS NULL OR NEW.incident_id IS NULL
       OR NEW.created_at IS NULL OR NEW.updated_at IS NULL THEN RETURN NEW; END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.confirmed_context_id IS NOT NULL OR NEW.updated_at IS DISTINCT FROM NEW.created_at THEN
            RAISE EXCEPTION 'Correlation group requires initial unconfirmed state'
              USING ERRCODE = '23514', CONSTRAINT = 'correlation_groups_initial_ck';
        END IF;
        RETURN NEW;
    END IF;
    IF ROW(NEW.id,NEW.scope_key,NEW.execution_id,NEW.incident_id,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.id,OLD.scope_key,OLD.execution_id,OLD.incident_id,OLD.created_at)
       OR NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION 'Correlation group identity and creation are immutable'
          USING ERRCODE = '23514', CONSTRAINT = 'correlation_groups_identity_ck';
    END IF;
    IF NEW.confirmed_context_id IS NOT DISTINCT FROM OLD.confirmed_context_id THEN
        IF NEW.updated_at IS DISTINCT FROM OLD.updated_at THEN
            RAISE EXCEPTION 'Correlation group timestamp requires a pointer change'
              USING ERRCODE = '23514', CONSTRAINT = 'correlation_groups_updated_ck';
        END IF;
        RETURN NEW;
    END IF;
    SELECT cardinality(evidence_ids) INTO next_revision FROM evidence.correlated_contexts
      WHERE id = NEW.confirmed_context_id AND group_id = OLD.id;
    SELECT cardinality(evidence_ids) INTO previous_revision FROM evidence.correlated_contexts
      WHERE id = OLD.confirmed_context_id AND group_id = OLD.id;
    IF next_revision IS NULL OR next_revision <> COALESCE(previous_revision, 0) + 1 THEN
        RAISE EXCEPTION 'Correlation confirmation must advance one revision in the same group'
          USING ERRCODE = '23514', CONSTRAINT = 'correlation_groups_confirmation_ck';
    END IF;
    RETURN NEW;
END; $$;

CREATE FUNCTION evidence.guard_correlation_snapshot() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, evidence AS $$
DECLARE g evidence.correlation_groups; previous evidence.correlated_contexts;
BEGIN
    IF NEW.id IS NULL OR NEW.group_id IS NULL OR NEW.incident_id IS NULL OR NEW.execution_id IS NULL
       OR NEW.evidence_ids IS NULL OR NEW.summary IS NULL OR NEW.correlation_id IS NULL OR NEW.created_at IS NULL
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

CREATE FUNCTION evidence.guard_correlation_receipt() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, evidence AS $$
DECLARE c evidence.correlated_contexts; partition text; original_context uuid;
BEGIN
    IF NEW.scope_key IS NULL OR NEW.producer_key IS NULL OR NEW.message_id IS NULL OR NEW.evidence_id IS NULL
       OR NEW.correlation_id IS NULL OR NEW.context_id IS NULL OR NEW.input_fingerprint IS NULL
       OR NEW.created_at IS NULL THEN RETURN NEW; END IF;
    SELECT * INTO c FROM evidence.correlated_contexts WHERE id = NEW.context_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Correlation receipt context reference is unavailable'
          USING ERRCODE = '23503', CONSTRAINT = 'correlation_receipts_context_id';
    END IF;
    SELECT scope_key INTO partition FROM evidence.correlation_groups WHERE id = c.group_id FOR UPDATE;
    IF partition IS DISTINCT FROM NEW.scope_key OR NOT NEW.evidence_id = ANY(c.evidence_ids) THEN
        RAISE EXCEPTION 'Correlation receipt scope or evidence membership mismatch'
          USING ERRCODE = '23514', CONSTRAINT = 'correlation_receipts_membership_ck';
    END IF;
    SELECT id INTO original_context FROM evidence.correlated_contexts
      WHERE group_id = c.group_id AND NEW.evidence_id = ANY(evidence_ids)
      ORDER BY cardinality(evidence_ids) LIMIT 1;
    IF NEW.context_id IS DISTINCT FROM original_context THEN
        RAISE EXCEPTION 'Correlation receipt must preserve the original member context'
          USING ERRCODE = '23514', CONSTRAINT = 'correlation_receipts_original_context_ck';
    END IF;
    RETURN NEW;
END; $$;

-- Deferred so the immutable snapshot and its source receipt commit atomically.
CREATE FUNCTION evidence.check_correlation_snapshot_receipt() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, evidence AS $$
DECLARE previous_members uuid[];
BEGIN
    SELECT evidence_ids INTO previous_members FROM evidence.correlated_contexts
      WHERE group_id = NEW.group_id AND cardinality(evidence_ids) = cardinality(NEW.evidence_ids) - 1;
    IF NOT EXISTS (SELECT 1 FROM evidence.correlation_receipts r
                   WHERE r.context_id = NEW.id AND r.evidence_id = ANY(NEW.evidence_ids)
                   AND NOT r.evidence_id = ANY(COALESCE(previous_members, ARRAY[]::uuid[]))) THEN
        RAISE EXCEPTION 'Correlation snapshot requires its new member receipt in the same transaction'
          USING ERRCODE = '23514', CONSTRAINT = 'correlated_contexts_receipt_ck';
    END IF;
    RETURN NULL;
END; $$;

CREATE TRIGGER correlation_groups_guard BEFORE INSERT OR UPDATE ON evidence.correlation_groups
FOR EACH ROW EXECUTE FUNCTION evidence.guard_correlation_group();
CREATE TRIGGER correlated_contexts_guard BEFORE INSERT ON evidence.correlated_contexts
FOR EACH ROW EXECUTE FUNCTION evidence.guard_correlation_snapshot();
CREATE TRIGGER correlation_receipts_guard BEFORE INSERT ON evidence.correlation_receipts
FOR EACH ROW EXECUTE FUNCTION evidence.guard_correlation_receipt();
CREATE CONSTRAINT TRIGGER correlated_contexts_receipt_required AFTER INSERT ON evidence.correlated_contexts
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION evidence.check_correlation_snapshot_receipt();

CREATE TRIGGER correlation_groups_immutable BEFORE DELETE OR TRUNCATE ON evidence.correlation_groups FOR EACH STATEMENT EXECUTE FUNCTION evidence.reject_correlation_mutation();
CREATE TRIGGER correlated_contexts_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON evidence.correlated_contexts FOR EACH STATEMENT EXECUTE FUNCTION evidence.reject_correlation_mutation();
CREATE TRIGGER correlation_receipts_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON evidence.correlation_receipts FOR EACH STATEMENT EXECUTE FUNCTION evidence.reject_correlation_mutation();
