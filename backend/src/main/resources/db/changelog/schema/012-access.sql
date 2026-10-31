--liquibase formatted sql

--changeset aciee:012-grants
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA core, ident, anon FROM PUBLIC;
REVOKE EXECUTE ON ALL PROCEDURES IN SCHEMA core FROM PUBLIC;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON ROUTINES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA ref GRANT SELECT ON TABLES TO aciee_ident, aciee_anon, aciee_report;
ALTER DEFAULT PRIVILEGES IN SCHEMA core, ident GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aciee_ident;
ALTER DEFAULT PRIVILEGES IN SCHEMA anon GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aciee_anon;

GRANT USAGE ON SCHEMA ref TO aciee_ident, aciee_anon, aciee_report;
GRANT SELECT ON ALL TABLES IN SCHEMA ref TO aciee_ident, aciee_anon, aciee_report;

GRANT USAGE ON SCHEMA core, ident TO aciee_ident;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core, ident TO aciee_ident;
REVOKE UPDATE, DELETE ON core.audit_log FROM aciee_ident;

GRANT USAGE ON SCHEMA core, anon TO aciee_anon;
GRANT SELECT ON core.faculty, core.department, core.specialization, core.academic_year, core.semester,
                core.subject, core.study_plan, core.curriculum_item, core.study_group, core.subgroup,
                core.course_offering, core.professor, core.teaching_assignment, core.offering_v, core.subject_root_v
    TO aciee_anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA anon TO aciee_anon;
REVOKE INSERT, UPDATE, DELETE ON anon.prof_subject_radar, anon.course_survival FROM aciee_anon;
GRANT EXECUTE ON FUNCTION anon.refresh_aggregates() TO aciee_anon;

GRANT USAGE ON SCHEMA anon TO aciee_report;
GRANT SELECT ON anon.prof_subject_radar, anon.course_survival TO aciee_report;

ALTER FUNCTION core.ensure_audit_partitions(integer) SECURITY DEFINER SET search_path = pg_catalog;
GRANT EXECUTE ON FUNCTION core.ensure_audit_partitions(integer), core.normalize_email(text) TO aciee_ident;
GRANT EXECUTE ON FUNCTION core.audit() TO aciee_ident;

ALTER ROLE aciee_ident SET statement_timeout = '5s';
ALTER ROLE aciee_ident SET lock_timeout = '2s';
ALTER ROLE aciee_ident SET idle_in_transaction_session_timeout = '10s';
ALTER ROLE aciee_anon SET statement_timeout = '5s';
ALTER ROLE aciee_anon SET lock_timeout = '2s';
ALTER ROLE aciee_anon SET idle_in_transaction_session_timeout = '10s';
ALTER ROLE aciee_report SET statement_timeout = '60s';
ALTER ROLE aciee_report SET lock_timeout = '2s';
ALTER ROLE aciee_report SET idle_in_transaction_session_timeout = '10s';
--rollback ALTER DEFAULT PRIVILEGES GRANT EXECUTE ON ROUTINES TO PUBLIC;
--rollback ALTER DEFAULT PRIVILEGES IN SCHEMA ref REVOKE SELECT ON TABLES FROM aciee_ident, aciee_anon, aciee_report;
--rollback ALTER DEFAULT PRIVILEGES IN SCHEMA core, ident REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM aciee_ident;
--rollback ALTER DEFAULT PRIVILEGES IN SCHEMA anon REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM aciee_anon;
--rollback REVOKE ALL ON ALL TABLES IN SCHEMA ref, core, ident, anon FROM aciee_ident, aciee_anon, aciee_report;
--rollback REVOKE ALL ON SCHEMA ref, core, ident, anon FROM aciee_ident, aciee_anon, aciee_report;
--rollback ALTER ROLE aciee_ident RESET ALL; ALTER ROLE aciee_anon RESET ALL; ALTER ROLE aciee_report RESET ALL;

--changeset aciee:012-rls
CREATE FUNCTION core.app_user_id() RETURNS integer
LANGUAGE sql STABLE PARALLEL SAFE
RETURN nullif(current_setting('app.user_id', true), '')::integer;

CREATE FUNCTION core.is_admin() RETURNS boolean
LANGUAGE sql STABLE
RETURN EXISTS (SELECT 1 FROM core.user_role r WHERE r.user_id = core.app_user_id() AND r.role_code = 'admin');

CREATE FUNCTION core.leads_group(target_group integer) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
RETURN EXISTS (SELECT 1
               FROM core.group_leader l
               JOIN core.group_membership m ON m.id = l.membership_id
               WHERE m.user_id = core.app_user_id() AND m.group_id = target_group
                 AND m.valid_during && daterange(current_date, NULL));

GRANT EXECUTE ON FUNCTION core.app_user_id(), core.is_admin(), core.leads_group(integer) TO aciee_ident;

ALTER TABLE core.group_membership ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_led_or_admin_read ON core.group_membership FOR SELECT
    USING (user_id = (SELECT core.app_user_id()) OR (SELECT core.is_admin()) OR core.leads_group(group_id));
CREATE POLICY led_or_admin_write ON core.group_membership FOR ALL
    USING ((SELECT core.is_admin()) OR core.leads_group(group_id))
    WITH CHECK ((SELECT core.is_admin()) OR core.leads_group(group_id));

ALTER TABLE core.membership_request ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_led_or_admin_read ON core.membership_request FOR SELECT
    USING (user_id = (SELECT core.app_user_id()) OR (SELECT core.is_admin()) OR core.leads_group(group_id));
CREATE POLICY own_pending_insert ON core.membership_request FOR INSERT
    WITH CHECK (user_id = (SELECT core.app_user_id()) AND status = 'pending');
CREATE POLICY admin_insert ON core.membership_request FOR INSERT
    WITH CHECK ((SELECT core.is_admin()) AND status = 'pending');
CREATE POLICY led_or_admin_decide ON core.membership_request FOR UPDATE
    USING ((SELECT core.is_admin()) OR core.leads_group(group_id))
    WITH CHECK ((SELECT core.is_admin()) OR core.leads_group(group_id));

ALTER TABLE ident.token_issuance ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_rows ON ident.token_issuance FOR ALL
    USING (user_id = (SELECT core.app_user_id()))
    WITH CHECK (user_id = (SELECT core.app_user_id()));
--rollback ALTER TABLE ident.token_issuance DISABLE ROW LEVEL SECURITY; DROP POLICY own_rows ON ident.token_issuance;
--rollback ALTER TABLE core.membership_request DISABLE ROW LEVEL SECURITY;
--rollback DROP POLICY led_or_admin_decide ON core.membership_request; DROP POLICY own_pending_insert ON core.membership_request;
--rollback DROP POLICY admin_insert ON core.membership_request;
--rollback DROP POLICY own_led_or_admin_read ON core.membership_request;
--rollback ALTER TABLE core.group_membership DISABLE ROW LEVEL SECURITY;
--rollback DROP POLICY led_or_admin_write ON core.group_membership; DROP POLICY own_led_or_admin_read ON core.group_membership;
--rollback DROP FUNCTION core.leads_group(integer), core.is_admin(), core.app_user_id();
