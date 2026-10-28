--liquibase formatted sql

--changeset aciee:004-organisation
CREATE TABLE core.faculty (
    id      integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code    text NOT NULL UNIQUE CHECK (length(code) <= 16),
    name_ro text NOT NULL CHECK (length(name_ro) <= 200)
);

CREATE TABLE core.department (
    id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    faculty_id integer NOT NULL REFERENCES core.faculty,
    name_ro    text NOT NULL CHECK (length(name_ro) <= 200),
    UNIQUE (faculty_id, name_ro)
);

CREATE TABLE core.specialization (
    id             integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    faculty_id     integer NOT NULL REFERENCES core.faculty,
    code           text NOT NULL UNIQUE CHECK (length(code) <= 16),
    name_ro        text NOT NULL CHECK (length(name_ro) <= 200),
    degree         text NOT NULL CHECK (degree IN ('BSc', 'MSc')),
    duration_years smallint NOT NULL CHECK (duration_years BETWEEN 1 AND 6)
);

CREATE TABLE core.academic_year (
    id     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    during daterange NOT NULL CHECK (NOT isempty(during)),
    EXCLUDE USING gist (during WITH &&)
);

CREATE TABLE core.semester (
    id               integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    academic_year_id integer NOT NULL REFERENCES core.academic_year,
    no               smallint NOT NULL CHECK (no IN (1, 2)),
    during           daterange NOT NULL CHECK (NOT isempty(during)),
    rating_opens_at  timestamptz NOT NULL,
    UNIQUE (academic_year_id, no),
    UNIQUE (academic_year_id, during WITHOUT OVERLAPS)
);
--rollback DROP TABLE core.semester, core.academic_year, core.specialization, core.department, core.faculty;
