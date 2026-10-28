--liquibase formatted sql

--changeset aciee:005-curriculum
CREATE TABLE core.subject (
    id             integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           text NOT NULL UNIQUE CHECK (length(code) <= 32),
    name_ro        text COLLATE core.ro_ro NOT NULL CHECK (length(name_ro) <= 200),
    name_en        text CHECK (length(name_en) <= 200),
    replaced_by_id integer REFERENCES core.subject,
    CHECK (replaced_by_id <> id)
);

CREATE TABLE core.study_plan (
    id                integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    specialization_id integer NOT NULL REFERENCES core.specialization,
    intake            int4range NOT NULL CHECK (NOT isempty(intake)),
    UNIQUE (specialization_id, intake WITHOUT OVERLAPS)
);

CREATE TABLE core.curriculum_item (
    id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id       integer NOT NULL REFERENCES core.study_plan,
    subject_id    integer NOT NULL REFERENCES core.subject,
    study_year    smallint NOT NULL CHECK (study_year >= 1),
    semester_no   smallint NOT NULL CHECK (semester_no IN (1, 2)),
    elective_slot smallint CHECK (elective_slot >= 1),
    lab_pct       ref.pct NOT NULL,
    exam_pct      ref.pct NOT NULL,
    project_pct   ref.pct NOT NULL,
    CHECK (lab_pct + exam_pct + project_pct = 100),
    UNIQUE (plan_id, subject_id),
    UNIQUE (id, plan_id, study_year)
);
CREATE INDEX curriculum_item_catalogue_idx ON core.curriculum_item (plan_id, study_year, semester_no);
CREATE INDEX curriculum_item_subject_idx ON core.curriculum_item (subject_id);

CREATE TABLE core.study_group (
    id               integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id          integer NOT NULL REFERENCES core.study_plan,
    academic_year_id integer NOT NULL REFERENCES core.academic_year,
    study_year       smallint NOT NULL CHECK (study_year >= 1),
    code             text NOT NULL CHECK (length(code) <= 16),
    max_size         smallint NOT NULL CHECK (max_size BETWEEN 1 AND 200),
    UNIQUE (academic_year_id, code),
    UNIQUE (id, plan_id, study_year)
);

CREATE TABLE core.subgroup (
    id       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id integer NOT NULL REFERENCES core.study_group,
    label    text NOT NULL CHECK (length(label) <= 16),
    UNIQUE (group_id, label),
    UNIQUE (id, group_id)
);

CREATE TABLE core.course_offering (
    id                 integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    curriculum_item_id integer NOT NULL,
    group_id           integer NOT NULL,
    plan_id            integer NOT NULL,
    study_year         smallint NOT NULL,
    title_snapshot     text NOT NULL CHECK (length(title_snapshot) <= 200),
    UNIQUE (curriculum_item_id, group_id),
    UNIQUE (group_id, id),
    FOREIGN KEY (curriculum_item_id, plan_id, study_year) REFERENCES core.curriculum_item (id, plan_id, study_year),
    FOREIGN KEY (group_id, plan_id, study_year) REFERENCES core.study_group (id, plan_id, study_year)
);
--rollback DROP TABLE core.course_offering, core.subgroup, core.study_group, core.curriculum_item, core.study_plan, core.subject;

--changeset aciee:005-study-year-trigger splitStatements:false
CREATE FUNCTION core.check_study_year() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.study_year > (SELECT s.duration_years
                         FROM core.study_plan p
                         JOIN core.specialization s ON s.id = p.specialization_id
                         WHERE p.id = NEW.plan_id) THEN
        RAISE EXCEPTION 'study_year % exceeds the specialization duration', NEW.study_year
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER study_year_within_duration
    BEFORE INSERT OR UPDATE OF plan_id, study_year ON core.curriculum_item
    FOR EACH ROW EXECUTE FUNCTION core.check_study_year();

CREATE TRIGGER study_year_within_duration
    BEFORE INSERT OR UPDATE OF plan_id, study_year ON core.study_group
    FOR EACH ROW EXECUTE FUNCTION core.check_study_year();
--rollback DROP TRIGGER study_year_within_duration ON core.study_group;
--rollback DROP TRIGGER study_year_within_duration ON core.curriculum_item;
--rollback DROP FUNCTION core.check_study_year();

--changeset aciee:005-views
CREATE VIEW core.offering_v AS
SELECT o.id AS offering_id,
       o.group_id,
       g.code AS group_code,
       g.academic_year_id,
       o.plan_id,
       o.study_year,
       ci.semester_no,
       s.id AS semester_id,
       s.rating_opens_at,
       ci.subject_id,
       sub.code AS subject_code,
       o.title_snapshot,
       ci.elective_slot,
       ci.lab_pct,
       ci.exam_pct,
       ci.project_pct
FROM core.course_offering o
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject sub ON sub.id = ci.subject_id
JOIN core.study_group g ON g.id = o.group_id
LEFT JOIN core.semester s ON s.academic_year_id = g.academic_year_id AND s.no = ci.semester_no;

CREATE VIEW core.subject_root_v AS
WITH RECURSIVE chain (subject_id, current_id, next_id) AS (
    SELECT id, id, replaced_by_id FROM core.subject
    UNION ALL
    SELECT c.subject_id, s.id, s.replaced_by_id
    FROM chain c
    JOIN core.subject s ON s.id = c.next_id
) CYCLE current_id SET is_cycle USING path
SELECT subject_id, current_id AS root_id
FROM chain
WHERE next_id IS NULL AND NOT is_cycle;
--rollback DROP VIEW core.subject_root_v; DROP VIEW core.offering_v;
