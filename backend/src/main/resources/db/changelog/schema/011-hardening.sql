--liquibase formatted sql

--changeset aciee:011-normalize-email
CREATE FUNCTION core.normalize_email(email text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE
RETURN lower(regexp_replace(email, '\+[^@]*@', '@'));

ALTER TABLE core.user_account ALTER COLUMN email_normalized SET EXPRESSION AS (core.normalize_email(email));
--rollback ALTER TABLE core.user_account ALTER COLUMN email_normalized SET EXPRESSION AS (lower(regexp_replace(email, '\+[^@]*@', '@')));
--rollback DROP FUNCTION core.normalize_email(text);

--changeset aciee:011-verification-sent-at
ALTER TABLE core.email_verification ADD COLUMN sent_at timestamptz NOT NULL DEFAULT now();
UPDATE core.email_verification SET sent_at = expires_at - interval '15 minutes';
ALTER TABLE core.email_verification DROP COLUMN expires_at;
--rollback ALTER TABLE core.email_verification ADD COLUMN expires_at timestamptz;
--rollback UPDATE core.email_verification SET expires_at = sent_at + interval '15 minutes';
--rollback ALTER TABLE core.email_verification ALTER COLUMN expires_at SET NOT NULL, DROP COLUMN sent_at;

--changeset aciee:011-audit-primary-keys splitStatements:false
CREATE OR REPLACE FUNCTION core.audit() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    actor integer := nullif(current_setting('app.user_id', true), '')::integer;
    keys  text[] := TG_ARGV[0:TG_NARGS - 1];
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO core.audit_log (actor_user_id, table_name, row_id, op, new)
        SELECT actor, TG_TABLE_NAME, (n.doc ->> keys[1])::integer, TG_OP, n.doc
        FROM (SELECT to_jsonb(r) AS doc FROM new_rows r) n;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO core.audit_log (actor_user_id, table_name, row_id, op, old)
        SELECT actor, TG_TABLE_NAME, (o.doc ->> keys[1])::integer, TG_OP, o.doc
        FROM (SELECT to_jsonb(r) AS doc FROM old_rows r) o;
    ELSE
        INSERT INTO core.audit_log (actor_user_id, table_name, row_id, op, old, new)
        SELECT actor, TG_TABLE_NAME, (n.doc ->> keys[1])::integer, TG_OP, o.doc, n.doc
        FROM (SELECT j.doc, (SELECT jsonb_agg(j.doc -> k) FROM unnest(keys) k) AS key
              FROM (SELECT to_jsonb(r) AS doc FROM old_rows r) j) o
        JOIN (SELECT j.doc, (SELECT jsonb_agg(j.doc -> k) FROM unnest(keys) k) AS key
              FROM (SELECT to_jsonb(r) AS doc FROM new_rows r) j) n ON n.key = o.key;
    END IF;
    RETURN NULL;
END
$$;

DROP PROCEDURE core.attach_audit(regclass, text);

CREATE PROCEDURE core.attach_audit(target regclass)
LANGUAGE plpgsql AS $$
DECLARE
    key_args text;
BEGIN
    SELECT string_agg(quote_literal(a.attname), ', ' ORDER BY k.ord)
    INTO key_args
    FROM pg_index i
    CROSS JOIN unnest(i.indkey) WITH ORDINALITY k(attnum, ord)
    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
    WHERE i.indrelid = target AND i.indisprimary;

    EXECUTE format('DROP TRIGGER IF EXISTS audit_insert ON %s', target);
    EXECUTE format('DROP TRIGGER IF EXISTS audit_update ON %s', target);
    EXECUTE format('DROP TRIGGER IF EXISTS audit_delete ON %s', target);
    EXECUTE format('CREATE TRIGGER audit_insert AFTER INSERT ON %s REFERENCING NEW TABLE AS new_rows '
                   'FOR EACH STATEMENT EXECUTE FUNCTION core.audit(%s)', target, key_args);
    EXECUTE format('CREATE TRIGGER audit_update AFTER UPDATE ON %s REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows '
                   'FOR EACH STATEMENT EXECUTE FUNCTION core.audit(%s)', target, key_args);
    EXECUTE format('CREATE TRIGGER audit_delete AFTER DELETE ON %s REFERENCING OLD TABLE AS old_rows '
                   'FOR EACH STATEMENT EXECUTE FUNCTION core.audit(%s)', target, key_args);
END
$$;

CALL core.attach_audit('core.subject');
CALL core.attach_audit('core.curriculum_item');
CALL core.attach_audit('core.study_group');
CALL core.attach_audit('core.course_offering');
CALL core.attach_audit('core.professor');
CALL core.attach_audit('core.teaching_assignment');
CALL core.attach_audit('core.user_role');
CALL core.attach_audit('core.group_invite');
CALL core.attach_audit('core.membership_request');
CALL core.attach_audit('core.group_membership');
CALL core.attach_audit('core.offering_enrolment');
CALL core.attach_audit('core.group_leader_claim');
CALL core.attach_audit('core.group_leader');
--rollback SELECT 1;

--changeset aciee:011-published-post-date
CREATE OR REPLACE VIEW anon.published_post AS
SELECT DISTINCT ON (p.id)
       p.id, p.offering_id, p.kind, p.teaching_assignment_id, v.version_no, v.body, v.moderated_week
FROM anon.post p
JOIN anon.post_version v ON v.post_id = p.id
WHERE v.status = 'approved'
  AND v.moderated_week < date_trunc('week', current_date)::date
  AND (p.kind = 'tip' OR EXISTS (
        SELECT 1
        FROM core.teaching_assignment ta
        JOIN core.course_offering o ON o.id = ta.offering_id
        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
        JOIN anon.prof_subject_radar r ON r.professor_id = ta.professor_id AND r.subject_id = sr.root_id
        CROSS JOIN ref.setting s
        WHERE ta.id = p.teaching_assignment_id AND r.n >= s.k_threshold))
ORDER BY p.id, v.version_no DESC;
--rollback SELECT 1;
