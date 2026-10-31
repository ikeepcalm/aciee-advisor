--liquibase formatted sql

--changeset aciee:014-component-hours
ALTER TABLE core.curriculum_item
    ADD COLUMN lecture_hours smallint NOT NULL DEFAULT 0 CHECK (lecture_hours BETWEEN 0 AND 20),
    ADD COLUMN seminar_hours smallint NOT NULL DEFAULT 0 CHECK (seminar_hours BETWEEN 0 AND 20),
    ADD COLUMN lab_hours smallint NOT NULL DEFAULT 0 CHECK (lab_hours BETWEEN 0 AND 20),
    ADD COLUMN project_hours smallint NOT NULL DEFAULT 0 CHECK (project_hours BETWEEN 0 AND 20);
--rollback ALTER TABLE core.curriculum_item DROP COLUMN lecture_hours, DROP COLUMN seminar_hours, DROP COLUMN lab_hours, DROP COLUMN project_hours;

--changeset aciee:014-rollover splitStatements:false
CREATE POLICY admin_insert ON core.membership_request FOR INSERT
    WITH CHECK ((SELECT core.is_admin()) AND status = 'pending');

CREATE FUNCTION core.rollover(from_year integer) RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE
    next_year integer;
    old       record;
    new_group integer;
    created   integer := 0;
BEGIN
    SELECT n.id INTO next_year
    FROM core.academic_year f
    JOIN core.academic_year n ON lower(n.during) = upper(f.during)
    WHERE f.id = from_year;
    IF next_year IS NULL THEN
        RAISE EXCEPTION 'academic year % has no following year', from_year USING ERRCODE = 'no_data_found';
    END IF;

    FOR old IN
        SELECT g.id, g.plan_id, g.study_year + 1 AS study_year, g.max_size, upper(ay.during) AS year_end,
               CASE WHEN substr(g.code, 2, 1) = g.study_year::text
                    THEN overlay(g.code PLACING (g.study_year + 1)::text FROM 2 FOR 1)
                    ELSE g.code END AS code
        FROM core.study_group g
        JOIN core.academic_year ay ON ay.id = g.academic_year_id
        JOIN core.study_plan p ON p.id = g.plan_id
        JOIN core.specialization s ON s.id = p.specialization_id
        WHERE g.academic_year_id = from_year AND g.study_year < s.duration_years
    LOOP
        INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
        VALUES (old.plan_id, next_year, old.study_year, old.code, old.max_size)
        ON CONFLICT (academic_year_id, code) DO NOTHING
        RETURNING id INTO new_group;
        CONTINUE WHEN new_group IS NULL;

        INSERT INTO core.subgroup (group_id, label)
        SELECT new_group, label FROM core.subgroup WHERE group_id = old.id;

        INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
        SELECT ci.id, new_group, ci.plan_id, ci.study_year, sub.name_ro
        FROM core.curriculum_item ci
        JOIN core.subject sub ON sub.id = ci.subject_id
        WHERE ci.plan_id = old.plan_id AND ci.study_year = old.study_year;

        INSERT INTO core.membership_request (user_id, group_id, subgroup_id, kind)
        SELECT m.user_id, new_group, ns.id, 'regular'
        FROM core.group_membership m
        LEFT JOIN core.subgroup os ON os.id = m.subgroup_id
        LEFT JOIN core.subgroup ns ON ns.group_id = new_group AND ns.label = os.label
        WHERE m.group_id = old.id AND upper(m.valid_during) = old.year_end AND m.kind <> 'historical_claim';

        INSERT INTO core.group_leader_claim (user_id, group_id)
        SELECT m.user_id, new_group
        FROM core.group_leader l
        JOIN core.group_membership m ON m.id = l.membership_id
        WHERE m.group_id = old.id;

        created := created + 1;
    END LOOP;
    RETURN created;
END
$$;

GRANT EXECUTE ON FUNCTION core.rollover(integer) TO aciee_ident;
--rollback DROP FUNCTION core.rollover(integer); DROP POLICY admin_insert ON core.membership_request;
