--liquibase formatted sql

--changeset aciee:007-accounts
CREATE TABLE core.user_account (
    id               integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email            text NOT NULL CHECK (length(email) <= 254),
    email_normalized text GENERATED ALWAYS AS (lower(regexp_replace(email, '\+[^@]*@', '@'))) STORED,
    password_hash    text,
    entra_oid        uuid UNIQUE,
    created_at       timestamptz NOT NULL DEFAULT now(),
    disabled_at      timestamptz,
    CONSTRAINT user_account_email_normalized_key UNIQUE (email_normalized),
    CHECK (email_normalized ~ '^[^@\s]+@student\.ugal\.ro$'),
    CHECK (password_hash IS NOT NULL OR entra_oid IS NOT NULL)
);

CREATE TABLE core.email_verification (
    user_id    integer PRIMARY KEY REFERENCES core.user_account ON DELETE CASCADE,
    code_hash  ref.sha256 NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts   smallint NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5)
);

CREATE TABLE core.user_role (
    user_id   integer NOT NULL REFERENCES core.user_account,
    role_code text NOT NULL REFERENCES ref.app_role,
    PRIMARY KEY (user_id, role_code)
);
--rollback DROP TABLE core.user_role, core.email_verification, core.user_account;

--changeset aciee:007-groups
CREATE TABLE core.group_invite (
    id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id   integer NOT NULL REFERENCES core.study_group,
    token_hash ref.sha256 NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    max_uses   smallint NOT NULL CHECK (max_uses >= 1),
    created_by integer NOT NULL REFERENCES core.user_account
);

CREATE TABLE core.membership_request (
    id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     integer NOT NULL REFERENCES core.user_account,
    group_id    integer NOT NULL REFERENCES core.study_group,
    subgroup_id integer,
    offering_id integer,
    kind        text NOT NULL REFERENCES ref.membership_kind,
    invite_id   integer REFERENCES core.group_invite,
    status      text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    decided_by  integer REFERENCES core.user_account,
    decided_at  timestamptz,
    CHECK ((status = 'pending') = (decided_at IS NULL)),
    FOREIGN KEY (group_id, offering_id) REFERENCES core.course_offering (group_id, id),
    FOREIGN KEY (subgroup_id, group_id) REFERENCES core.subgroup (id, group_id)
);
CREATE UNIQUE INDEX membership_request_one_pending_idx ON core.membership_request (user_id, group_id) WHERE status = 'pending';
CREATE INDEX membership_request_group_pending_idx ON core.membership_request (group_id) WHERE status = 'pending';
CREATE INDEX membership_request_user_idx ON core.membership_request (user_id);
CREATE INDEX membership_request_invite_idx ON core.membership_request (invite_id);

CREATE TABLE core.group_membership (
    id           integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      integer NOT NULL REFERENCES core.user_account,
    group_id     integer NOT NULL REFERENCES core.study_group,
    subgroup_id  integer,
    kind         text NOT NULL REFERENCES ref.membership_kind,
    source       text NOT NULL CHECK (source IN ('request', 'university_sync')),
    request_id   integer UNIQUE REFERENCES core.membership_request,
    valid_during daterange NOT NULL CHECK (NOT isempty(valid_during)),
    CHECK ((source = 'request') = (request_id IS NOT NULL)),
    UNIQUE (user_id, valid_during WITHOUT OVERLAPS),
    FOREIGN KEY (subgroup_id, group_id) REFERENCES core.subgroup (id, group_id)
);

CREATE TABLE core.offering_enrolment (
    user_id     integer NOT NULL REFERENCES core.user_account,
    offering_id integer NOT NULL REFERENCES core.course_offering,
    request_id  integer NOT NULL UNIQUE REFERENCES core.membership_request,
    PRIMARY KEY (user_id, offering_id)
);

CREATE TABLE core.group_leader_claim (
    id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    integer NOT NULL REFERENCES core.user_account,
    group_id   integer NOT NULL REFERENCES core.study_group,
    status     text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    decided_by integer REFERENCES core.user_account,
    decided_at timestamptz,
    CHECK ((status = 'pending') = (decided_at IS NULL))
);

CREATE TABLE core.group_leader (
    membership_id integer PRIMARY KEY REFERENCES core.group_membership,
    claim_id      integer NOT NULL UNIQUE REFERENCES core.group_leader_claim
);
--rollback DROP TABLE core.group_leader, core.group_leader_claim, core.offering_enrolment, core.group_membership,
--rollback     core.membership_request, core.group_invite;

--changeset aciee:007-spring-session
CREATE TABLE core.spring_session (
    primary_id            char(36) PRIMARY KEY,
    session_id            char(36) NOT NULL UNIQUE,
    creation_time         bigint NOT NULL,
    last_access_time      bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time           bigint NOT NULL,
    principal_name        varchar(100)
);
CREATE INDEX spring_session_expiry_idx ON core.spring_session (expiry_time);
CREATE INDEX spring_session_principal_idx ON core.spring_session (principal_name);

CREATE TABLE core.spring_session_attributes (
    session_primary_id char(36) NOT NULL REFERENCES core.spring_session ON DELETE CASCADE,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);
--rollback DROP TABLE core.spring_session_attributes, core.spring_session;
