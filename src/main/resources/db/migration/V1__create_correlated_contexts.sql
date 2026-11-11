CREATE SCHEMA IF NOT EXISTS evidence;

CREATE TABLE evidence.correlated_contexts (
    id uuid PRIMARY KEY,
    incident_id uuid NOT NULL,
    execution_id uuid NOT NULL,
    evidence_ids uuid[] NOT NULL,
    summary text NOT NULL,
    correlation_id text NOT NULL,
    created_at timestamptz NOT NULL
);
