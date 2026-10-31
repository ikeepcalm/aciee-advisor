--liquibase formatted sql

--changeset aciee:014-component-hours
ALTER TABLE core.curriculum_item
    ADD COLUMN lecture_hours smallint NOT NULL DEFAULT 0 CHECK (lecture_hours BETWEEN 0 AND 20),
    ADD COLUMN seminar_hours smallint NOT NULL DEFAULT 0 CHECK (seminar_hours BETWEEN 0 AND 20),
    ADD COLUMN lab_hours smallint NOT NULL DEFAULT 0 CHECK (lab_hours BETWEEN 0 AND 20),
    ADD COLUMN project_hours smallint NOT NULL DEFAULT 0 CHECK (project_hours BETWEEN 0 AND 20);
--rollback ALTER TABLE core.curriculum_item DROP COLUMN lecture_hours, DROP COLUMN seminar_hours, DROP COLUMN lab_hours, DROP COLUMN project_hours;

--changeset aciee:014-rollover splitStatements:false
CREATE FUNCTION core.rollover(from_year integer) RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE
    next_year integer;
    created   integer;
BEGIN
    SELECT n.id INTO next_year
    FROM core.academic_year f
    JOIN core.academic_year n ON lower(n.during) = upper(f.during)
    WHERE f.id = from_year;
    IF next_year IS NULL THEN
        RETURN NULL;
    END IF;

    WITH source AS (
        SELECT g.id AS old_id, g.plan_id, g.study_year + 1 AS study_year, g.max_size, upper(ay.during) AS year_end,
               CASE WHEN substr(g.code, 2, 1) = g.study_year::text
                    THEN overlay(g.code PLACING (g.study_year + 1)::text FROM 2 FOR 1)
                    ELSE g.code END AS code
        FROM core.study_group g
        JOIN core.academic_year ay ON ay.id = g.academic_year_id
        JOIN core.study_plan p ON p.id = g.plan_id
        JOIN core.specialization s ON s.id = p.specialization_id
        WHERE g.academic_year_id = from_year AND g.study_year < s.duration_years
    ), groups AS (
        INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
        SELECT plan_id, next_year, study_year, code, max_size FROM source
        ON CONFLICT (academic_year_id, code) DO NOTHING
        RETURNING id, code
    ), pairs AS (
        SELECT s.old_id, g.id AS new_id, s.plan_id, s.study_year, s.year_end
        FROM source s JOIN groups g ON g.code = s.code
    ), subgroups AS (
        INSERT INTO core.subgroup (group_id, label)
        SELECT p.new_id, sg.label FROM pairs p JOIN core.subgroup sg ON sg.group_id = p.old_id
        RETURNING id, group_id, label
    ), offerings AS (
        INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
        SELECT ci.id, p.new_id, ci.plan_id, ci.study_year, sub.name_ro
        FROM pairs p
        JOIN core.curriculum_item ci ON ci.plan_id = p.plan_id AND ci.study_year = p.study_year
        JOIN core.subject sub ON sub.id = ci.subject_id
    ), requests AS (
        INSERT INTO core.membership_request (user_id, group_id, subgroup_id, kind)
        SELECT m.user_id, p.new_id, ns.id, 'regular'
        FROM pairs p
        JOIN core.group_membership m ON m.group_id = p.old_id AND upper(m.valid_during) = p.year_end
                                     AND m.kind <> 'historical_claim'
        LEFT JOIN core.subgroup os ON os.id = m.subgroup_id
        LEFT JOIN subgroups ns ON ns.group_id = p.new_id AND ns.label = os.label
    ), claims AS (
        INSERT INTO core.group_leader_claim (user_id, group_id)
        SELECT m.user_id, p.new_id
        FROM pairs p
        JOIN core.group_membership m ON m.group_id = p.old_id
        JOIN core.group_leader l ON l.membership_id = m.id
    )
    SELECT count(*) INTO created FROM pairs;
    RETURN created;
END
$$;

GRANT EXECUTE ON FUNCTION core.rollover(integer) TO aciee_ident;
--rollback DROP FUNCTION core.rollover(integer);

--changeset aciee:014-archive-professor splitStatements:false
CREATE FUNCTION core.archive_professor(target integer) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
BEGIN
    UPDATE core.professor
    SET archived_at = now(), full_name = 'Profesor arhivat', slug = 'arhivat-' || id
    WHERE id = target AND archived_at IS NULL;
    IF NOT FOUND THEN
        RETURN false;
    END IF;
    UPDATE core.audit_log
    SET old = old - 'full_name' - 'slug', new = new - 'full_name' - 'slug'
    WHERE table_name = 'professor' AND row_id = target;
    RETURN true;
END
$$;

GRANT EXECUTE ON FUNCTION core.archive_professor(integer) TO aciee_ident;
--rollback DROP FUNCTION core.archive_professor(integer);

--changeset aciee:014-audit-history-index
CREATE INDEX audit_log_table_history_idx ON core.audit_log (table_name, id);
--rollback DROP INDEX core.audit_log_table_history_idx;
