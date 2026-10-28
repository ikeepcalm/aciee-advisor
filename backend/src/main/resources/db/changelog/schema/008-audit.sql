--liquibase formatted sql

--changeset aciee:008-audit-log
CREATE TABLE core.audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY,
    created_at    timestamptz NOT NULL DEFAULT now(),
    actor_user_id integer REFERENCES core.user_account,
    table_name    text NOT NULL,
    row_id        integer NOT NULL,
    op            text NOT NULL CHECK (op IN ('INSERT', 'UPDATE', 'DELETE')),
    old           jsonb,
    new           jsonb,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
CREATE INDEX audit_log_row_idx ON core.audit_log (table_name, row_id);
CREATE INDEX audit_log_created_brin ON core.audit_log USING brin (created_at);
--rollback DROP TABLE core.audit_log;

--changeset aciee:008-audit-partitions splitStatements:false
CREATE FUNCTION core.ensure_audit_partitions(months_ahead integer) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    month date;
BEGIN
    FOR month IN
        SELECT generate_series(date_trunc('month', now() AT TIME ZONE 'UTC'),
                               date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => months_ahead),
                               interval '1 month')::date
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS core.%I PARTITION OF core.audit_log FOR VALUES FROM (%L) TO (%L)',
                       'audit_log_' || to_char(month, 'YYYY_MM'),
                       month || ' 00:00 UTC',
                       (month + interval '1 month')::date || ' 00:00 UTC');
    END LOOP;
END
$$;

CREATE FUNCTION core.retire_audit_partitions(keep_months integer) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    partition_name text;
BEGIN
    FOR partition_name IN
        SELECT c.relname
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'core.audit_log'::regclass
          AND to_date(right(c.relname, 7), 'YYYY_MM')
              < date_trunc('month', now() AT TIME ZONE 'UTC') - make_interval(months => keep_months)
    LOOP
        EXECUTE format('ALTER TABLE core.audit_log DETACH PARTITION core.%I', partition_name);
        EXECUTE format('DROP TABLE core.%I', partition_name);
    END LOOP;
END
$$;

SELECT core.ensure_audit_partitions(3);
--rollback DROP FUNCTION core.retire_audit_partitions(integer); DROP FUNCTION core.ensure_audit_partitions(integer);

--changeset aciee:008-audit-trigger splitStatements:false
CREATE FUNCTION core.audit() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    actor integer := nullif(current_setting('app.user_id', true), '')::integer;
    key   text := coalesce(TG_ARGV[0], 'id');
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO core.audit_log (actor_user_id, table_name, row_id, op, new)
        SELECT actor, TG_TABLE_NAME, (to_jsonb(n) ->> key)::integer, TG_OP, to_jsonb(n)
        FROM new_rows n;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO core.audit_log (actor_user_id, table_name, row_id, op, old)
        SELECT actor, TG_TABLE_NAME, (to_jsonb(o) ->> key)::integer, TG_OP, to_jsonb(o)
        FROM old_rows o;
    ELSE
        INSERT INTO core.audit_log (actor_user_id, table_name, row_id, op, old, new)
        SELECT actor, TG_TABLE_NAME, (to_jsonb(n) ->> key)::integer, TG_OP, to_jsonb(o), to_jsonb(n)
        FROM old_rows o
        JOIN new_rows n ON to_jsonb(o) -> key = to_jsonb(n) -> key;
    END IF;
    RETURN NULL;
END
$$;

CREATE PROCEDURE core.attach_audit(target regclass, key_column text DEFAULT 'id')
LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE format('CREATE TRIGGER audit_insert AFTER INSERT ON %s REFERENCING NEW TABLE AS new_rows '
                   'FOR EACH STATEMENT EXECUTE FUNCTION core.audit(%L)', target, key_column);
    EXECUTE format('CREATE TRIGGER audit_update AFTER UPDATE ON %s REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows '
                   'FOR EACH STATEMENT EXECUTE FUNCTION core.audit(%L)', target, key_column);
    EXECUTE format('CREATE TRIGGER audit_delete AFTER DELETE ON %s REFERENCING OLD TABLE AS old_rows '
                   'FOR EACH STATEMENT EXECUTE FUNCTION core.audit(%L)', target, key_column);
END
$$;

CALL core.attach_audit('core.subject');
CALL core.attach_audit('core.curriculum_item');
CALL core.attach_audit('core.study_group');
CALL core.attach_audit('core.course_offering');
CALL core.attach_audit('core.professor');
CALL core.attach_audit('core.teaching_assignment');
CALL core.attach_audit('core.user_role', 'user_id');
CALL core.attach_audit('core.group_invite');
CALL core.attach_audit('core.membership_request');
CALL core.attach_audit('core.group_membership');
CALL core.attach_audit('core.offering_enrolment', 'user_id');
CALL core.attach_audit('core.group_leader_claim');
CALL core.attach_audit('core.group_leader', 'membership_id');
--rollback DROP PROCEDURE core.attach_audit(regclass, text); DROP FUNCTION core.audit() CASCADE;
