--liquibase formatted sql

--changeset aciee:019-timetable-changes
CREATE TABLE core.timetable_slot (
    program_code text     NOT NULL,
    group_code   text     NOT NULL,
    day          smallint NOT NULL CHECK (day BETWEEN 1 AND 7),
    start_time   text     NOT NULL,
    end_time     text     NOT NULL,
    weeks        text     NOT NULL CHECK (weeks IN ('ALL', 'ODD', 'EVEN')),
    kind         text,
    subject      text     NOT NULL,
    room         text,
    teacher      text
);
CREATE INDEX timetable_slot_program_idx ON core.timetable_slot (program_code);

CREATE TABLE core.timetable_change (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    program_code text        NOT NULL,
    group_code   text        NOT NULL,
    detected_at  timestamptz NOT NULL DEFAULT now(),
    change       text        NOT NULL CHECK (change IN ('added', 'removed')),
    day          smallint    NOT NULL CHECK (day BETWEEN 1 AND 7),
    start_time   text        NOT NULL,
    end_time     text        NOT NULL,
    weeks        text        NOT NULL CHECK (weeks IN ('ALL', 'ODD', 'EVEN')),
    kind         text,
    subject      text        NOT NULL,
    room         text,
    teacher      text
);
CREATE INDEX timetable_change_group_idx ON core.timetable_change (program_code, group_code, detected_at DESC);
--rollback DROP TABLE core.timetable_change; DROP TABLE core.timetable_slot;
