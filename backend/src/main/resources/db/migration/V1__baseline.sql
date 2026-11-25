-- ST Atlas baseline schema (slice 1). See docs/architecture/DATA_MODEL.md.
-- Workspace isolation: every workspace-owned row carries workspace_id, and
-- children reference parents through (workspace_id, id) so a row can never
-- point at a parent in another workspace, even if application code had a bug.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ------------------------------------------------------------------ identity
CREATE TABLE app_user (
    id              uuid PRIMARY KEY,
    username        text        NOT NULL CHECK (length(username) BETWEEN 3 AND 64),
    display_name    text        NOT NULL CHECK (length(display_name) BETWEEN 1 AND 120),
    password_hash   text        NOT NULL,
    mfa_secret_enc  text,
    mfa_enabled     boolean     NOT NULL DEFAULT false,
    locale          text        NOT NULL DEFAULT 'en' CHECK (locale IN ('en', 'es')),
    time_zone       text        NOT NULL DEFAULT 'UTC',
    is_admin        boolean     NOT NULL DEFAULT false,
    enabled         boolean     NOT NULL DEFAULT true,
    failed_logins   integer     NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- Usernames are unique case-insensitively.
CREATE UNIQUE INDEX ux_app_user_username ON app_user (lower(username));

CREATE TABLE recovery_code (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code_hash   text        NOT NULL,
    used_at     timestamptz
);
CREATE INDEX ix_recovery_code_user ON recovery_code (user_id);

CREATE TABLE instance_setting (
    key         text PRIMARY KEY,
    value       text NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Spring Session JDBC (persistent, revocable sessions; ADR 0002)
CREATE TABLE spring_session (
    primary_id            char(36) NOT NULL,
    session_id            char(36) NOT NULL,
    creation_time         bigint   NOT NULL,
    last_access_time      bigint   NOT NULL,
    max_inactive_interval int      NOT NULL,
    expiry_time           bigint   NOT NULL,
    principal_name        varchar(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);
CREATE TABLE spring_session_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------- workspace
CREATE TABLE workspace (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE workspace_member (
    workspace_id uuid NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    user_id      uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role         text NOT NULL CHECK (role IN ('MANAGER', 'ANALYST', 'REVIEWER', 'VIEWER')),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX ix_workspace_member_user ON workspace_member (user_id);

-- -------------------------------------------------------------------- storage
CREATE TABLE attachment (
    id            uuid PRIMARY KEY,
    workspace_id  uuid        NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    storage_key   text        NOT NULL UNIQUE,
    original_name text        NOT NULL CHECK (length(original_name) <= 255),
    mime_type     text        NOT NULL,
    size_bytes    bigint      NOT NULL CHECK (size_bytes >= 0),
    sha256        text        NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    uploaded_by   uuid        NOT NULL REFERENCES app_user (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id)
);

-- --------------------------------------------------------------------- client
CREATE TABLE client (
    id                 uuid PRIMARY KEY,
    workspace_id       uuid        NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    name               text        NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    legal_name         text        CHECK (length(legal_name) <= 300),
    website            text        CHECK (length(website) <= 300),
    notes              text        CHECK (length(notes) <= 20000),
    logo_attachment_id uuid,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, logo_attachment_id) REFERENCES attachment (workspace_id, id)
);
CREATE INDEX ix_client_workspace ON client (workspace_id);

CREATE TABLE contact (
    id           uuid PRIMARY KEY,
    workspace_id uuid NOT NULL,
    client_id    uuid NOT NULL,
    name         text NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    email        text CHECK (length(email) <= 254),
    phone        text CHECK (length(phone) <= 50),
    kind         text NOT NULL CHECK (kind IN ('TECHNICAL', 'ADMINISTRATIVE', 'OTHER')),
    FOREIGN KEY (workspace_id, client_id) REFERENCES client (workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_contact_client ON contact (client_id);

-- ----------------------------------------------------------------- assessment
CREATE TABLE assessment_type (
    code          text PRIMARY KEY,
    uses_cvss     boolean NOT NULL,
    sort_order    integer NOT NULL,
    enabled       boolean NOT NULL DEFAULT true
);
INSERT INTO assessment_type (code, uses_cvss, sort_order) VALUES
    ('WEB_PENTEST', true, 1), ('API_PENTEST', true, 2), ('NETWORK_PENTEST', true, 3),
    ('ACTIVE_DIRECTORY', true, 4), ('MOBILE_SECURITY', true, 5), ('WIRELESS', true, 6),
    ('VULNERABILITY_ASSESSMENT', true, 7), ('DIGITAL_FORENSICS', false, 8),
    ('INCIDENT_RESPONSE', false, 9), ('SOC_INVESTIGATION', false, 10), ('NOC_ASSESSMENT', false, 11),
    ('INFRASTRUCTURE_SECURITY', true, 12), ('SYSTEM_HARDENING', false, 13), ('CLOUD_SECURITY', true, 14),
    ('SECURE_CODE_REVIEW', true, 15), ('COMPLIANCE_AUDIT', false, 16), ('CTF_WRITEUP', false, 17),
    ('CYBERSECURITY_LAB', false, 18), ('TECHNICAL_RESEARCH', false, 19), ('CUSTOM', false, 20);

CREATE TABLE assessment (
    id                  uuid PRIMARY KEY,
    workspace_id        uuid        NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    code                text        NOT NULL,
    title               text        NOT NULL CHECK (length(title) BETWEEN 1 AND 300),
    description         text        CHECK (length(description) <= 20000),
    type_code           text        NOT NULL REFERENCES assessment_type (code),
    client_id           uuid,
    status              text        NOT NULL DEFAULT 'DRAFT' CHECK (status IN
                        ('DRAFT', 'PLANNED', 'IN_PROGRESS', 'TECHNICAL_REVIEW', 'APPROVED',
                         'DELIVERED', 'RETESTING', 'COMPLETED', 'ARCHIVED')),
    start_date          date,
    due_date            date,
    completed_date      date,
    objectives          text CHECK (length(objectives) <= 20000),
    scope               text CHECK (length(scope) <= 20000),
    out_of_scope        text CHECK (length(out_of_scope) <= 20000),
    rules_of_engagement text CHECK (length(rules_of_engagement) <= 20000),
    methodology         text CHECK (length(methodology) <= 20000),
    tags                text[]      NOT NULL DEFAULT '{}',
    created_by          uuid        NOT NULL REFERENCES app_user (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id),
    UNIQUE (workspace_id, code),
    CHECK (due_date IS NULL OR start_date IS NULL OR due_date >= start_date),
    FOREIGN KEY (workspace_id, client_id) REFERENCES client (workspace_id, id)
);
CREATE INDEX ix_assessment_workspace_status ON assessment (workspace_id, status);
CREATE INDEX ix_assessment_tags ON assessment USING gin (tags);

CREATE TABLE assessment_analyst (
    assessment_id uuid NOT NULL REFERENCES assessment (id) ON DELETE CASCADE,
    user_id       uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (assessment_id, user_id)
);

CREATE TABLE asset (
    id            uuid PRIMARY KEY,
    workspace_id  uuid    NOT NULL,
    assessment_id uuid    NOT NULL,
    name          text    NOT NULL CHECK (length(name) BETWEEN 1 AND 300),
    kind          text    NOT NULL CHECK (length(kind) BETWEEN 1 AND 60),
    identifier    text    CHECK (length(identifier) <= 500),
    in_scope      boolean NOT NULL DEFAULT true,
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, assessment_id) REFERENCES assessment (workspace_id, id) ON DELETE CASCADE
);

-- -------------------------------------------------------------------- finding
CREATE TABLE finding (
    id                uuid PRIMARY KEY,
    workspace_id      uuid        NOT NULL,
    assessment_id     uuid        NOT NULL,
    code              text        NOT NULL,
    title             text        NOT NULL CHECK (length(title) BETWEEN 1 AND 300),
    description       text        CHECK (length(description) <= 50000),
    technical_details text        CHECK (length(technical_details) <= 100000),
    severity          text        NOT NULL CHECK (severity IN
                      ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFORMATIONAL', 'NOT_APPLICABLE')),
    cvss31_vector     text        CHECK (length(cvss31_vector) <= 200),
    cvss31_score      numeric(3, 1) CHECK (cvss31_score BETWEEN 0 AND 10),
    cvss40_vector     text        CHECK (length(cvss40_vector) <= 300),
    cvss40_score      numeric(3, 1) CHECK (cvss40_score BETWEEN 0 AND 10),
    cwe               text[]      NOT NULL DEFAULT '{}',
    cve               text[]      NOT NULL DEFAULT '{}',
    impact            text        CHECK (length(impact) <= 20000),
    recommendation    text        CHECK (length(recommendation) <= 20000),
    "references"      text[]      NOT NULL DEFAULT '{}',
    tags              text[]      NOT NULL DEFAULT '{}',
    status            text        NOT NULL DEFAULT 'OPEN' CHECK (status IN
                      ('OPEN', 'IN_PROGRESS', 'MITIGATED', 'FIXED', 'RISK_ACCEPTED',
                       'FALSE_POSITIVE', 'PENDING_RETEST')),
    discovered_on     date,
    analyst_id        uuid REFERENCES app_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id),
    UNIQUE (assessment_id, code),
    FOREIGN KEY (workspace_id, assessment_id) REFERENCES assessment (workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_finding_assessment ON finding (assessment_id);
CREATE INDEX ix_finding_workspace_severity ON finding (workspace_id, severity);
CREATE INDEX ix_finding_tags ON finding USING gin (tags);

CREATE TABLE finding_asset (
    workspace_id uuid NOT NULL,
    finding_id   uuid NOT NULL,
    asset_id     uuid NOT NULL,
    PRIMARY KEY (finding_id, asset_id),
    FOREIGN KEY (workspace_id, finding_id) REFERENCES finding (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, asset_id) REFERENCES asset (workspace_id, id) ON DELETE CASCADE
);

-- --------------------------------------------------------------------- report
CREATE TABLE report_template (
    id            uuid PRIMARY KEY,
    workspace_id  uuid REFERENCES workspace (id) ON DELETE CASCADE, -- NULL = built-in
    name          text        NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
    config        jsonb       NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE report (
    id              uuid PRIMARY KEY,
    workspace_id    uuid        NOT NULL,
    assessment_id   uuid        NOT NULL,
    title           text        NOT NULL CHECK (length(title) BETWEEN 1 AND 300),
    template_id     uuid        REFERENCES report_template (id),
    document        jsonb       NOT NULL,
    revision        integer     NOT NULL DEFAULT 0, -- optimistic locking for autosave
    classification  text        NOT NULL DEFAULT 'CONFIDENTIAL' CHECK (classification IN
                    ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')),
    created_by      uuid        NOT NULL REFERENCES app_user (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, assessment_id) REFERENCES assessment (workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_report_assessment ON report (assessment_id);

CREATE TABLE report_version (
    id                uuid PRIMARY KEY,
    workspace_id      uuid        NOT NULL,
    report_id         uuid        NOT NULL,
    number            integer     NOT NULL CHECK (number >= 1),
    document          jsonb       NOT NULL,
    template_config   jsonb       NOT NULL,
    pdf_attachment_id uuid,
    page_count        integer,
    finalized         boolean     NOT NULL DEFAULT false,
    created_by        uuid        NOT NULL REFERENCES app_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (report_id, number),
    FOREIGN KEY (workspace_id, report_id) REFERENCES report (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, pdf_attachment_id) REFERENCES attachment (workspace_id, id)
);

-- Finalised versions are immutable through any path, not only the API (T-15).
CREATE FUNCTION forbid_finalized_version_change() RETURNS trigger AS $$
BEGIN
    IF OLD.finalized THEN
        RAISE EXCEPTION 'report_version % is finalized and immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_report_version_immutable
    BEFORE UPDATE ON report_version
    FOR EACH ROW EXECUTE FUNCTION forbid_finalized_version_change();

-- --------------------------------------------------------------- timetracking
CREATE TABLE time_entry (
    id               uuid PRIMARY KEY,
    workspace_id     uuid        NOT NULL,
    assessment_id    uuid        NOT NULL,
    user_id          uuid        NOT NULL REFERENCES app_user (id),
    started_at       timestamptz NOT NULL,
    ended_at         timestamptz,
    duration_seconds bigint      CHECK (duration_seconds >= 0),
    billable         boolean     NOT NULL DEFAULT true,
    description      text        CHECK (length(description) <= 2000),
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK (ended_at IS NULL OR ended_at >= started_at),
    FOREIGN KEY (workspace_id, assessment_id) REFERENCES assessment (workspace_id, id) ON DELETE CASCADE
);
-- One running timer per user at most.
CREATE UNIQUE INDEX ux_time_entry_running ON time_entry (user_id) WHERE ended_at IS NULL;
CREATE INDEX ix_time_entry_assessment ON time_entry (assessment_id);

-- --------------------------------------------------------------------- search
-- array_to_string is STABLE in general, but for text[] it is deterministic; generated
-- columns require IMMUTABLE, hence this narrow wrapper.
CREATE FUNCTION atlas_tags_text(tags text[]) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT array_to_string(tags, ' ') $$;

CREATE TABLE search_entry (
    id            uuid PRIMARY KEY,
    workspace_id  uuid        NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    entity_type   text        NOT NULL,
    entity_id     uuid        NOT NULL,
    assessment_id uuid,
    title         text        NOT NULL,
    body          text        NOT NULL DEFAULT '',
    tags          text[]      NOT NULL DEFAULT '{}',
    severity      text,
    status        text,
    type_code     text,
    client_id     uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    tsv_en tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', atlas_tags_text(tags)), 'B') ||
        setweight(to_tsvector('english', coalesce(body, '')), 'C')) STORED,
    tsv_es tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('spanish', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('spanish', atlas_tags_text(tags)), 'B') ||
        setweight(to_tsvector('spanish', coalesce(body, '')), 'C')) STORED,
    UNIQUE (entity_type, entity_id)
);
CREATE INDEX ix_search_tsv_en ON search_entry USING gin (tsv_en);
CREATE INDEX ix_search_tsv_es ON search_entry USING gin (tsv_es);
CREATE INDEX ix_search_title_trgm ON search_entry USING gin (title gin_trgm_ops);
CREATE INDEX ix_search_tags ON search_entry USING gin (tags);
CREATE INDEX ix_search_workspace ON search_entry (workspace_id, updated_at DESC);

-- ---------------------------------------------------------------------- audit
CREATE TABLE audit_event (
    id           uuid PRIMARY KEY,
    workspace_id uuid,
    actor_id     uuid,
    action       text        NOT NULL,
    entity_type  text,
    entity_id    uuid,
    ip           text,
    at           timestamptz NOT NULL DEFAULT now(),
    details      jsonb       NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_audit_workspace_at ON audit_event (workspace_id, at DESC);

-- Append-only: no updates or deletes through the application role (T-16).
CREATE FUNCTION forbid_audit_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_change();
