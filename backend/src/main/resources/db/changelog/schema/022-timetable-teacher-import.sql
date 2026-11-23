--liquibase formatted sql

--changeset aciee:022-timetable-teacher-import
INSERT INTO ref.academic_title VALUES ('cadru', 'Cadru didactic');

ALTER TABLE core.professor ADD COLUMN name_confirmed boolean NOT NULL DEFAULT true;

CREATE TABLE core.timetable_import (
    offering_id  integer NOT NULL REFERENCES core.course_offering ON DELETE CASCADE,
    component    text    NOT NULL REFERENCES ref.component,
    teacher      text    NOT NULL,
    professor_id integer NOT NULL REFERENCES core.professor ON DELETE CASCADE,
    applied_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (offering_id, component)
);
--rollback DROP TABLE core.timetable_import; ALTER TABLE core.professor DROP COLUMN name_confirmed; DELETE FROM ref.academic_title WHERE code = 'cadru';
