--liquibase formatted sql

--changeset aciee:006-teaching
CREATE TABLE core.professor (
    id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name     text COLLATE core.ro_ro NOT NULL CHECK (length(full_name) <= 200),
    title_code    text NOT NULL REFERENCES ref.academic_title,
    department_id integer NOT NULL REFERENCES core.department,
    slug          text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    archived_at   timestamptz
);

CREATE TABLE core.teaching_assignment (
    id           integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id  integer NOT NULL,
    group_id     integer NOT NULL,
    professor_id integer NOT NULL REFERENCES core.professor ON DELETE RESTRICT,
    component    text NOT NULL REFERENCES ref.component,
    subgroup_id  integer,
    valid_during daterange NOT NULL CHECK (NOT isempty(valid_during)),
    CHECK (component <> 'lecture' OR subgroup_id IS NULL),
    UNIQUE (offering_id, id),
    FOREIGN KEY (group_id, offering_id) REFERENCES core.course_offering (group_id, id),
    FOREIGN KEY (subgroup_id, group_id) REFERENCES core.subgroup (id, group_id)
);
CREATE INDEX teaching_assignment_professor_idx ON core.teaching_assignment (professor_id);
CREATE STATISTICS core.teaching_assignment_offering_group (dependencies)
    ON offering_id, group_id FROM core.teaching_assignment;
--rollback DROP TABLE core.teaching_assignment, core.professor;
