--liquibase formatted sql

--changeset aciee:020-semester-breaks
ALTER TABLE core.semester ADD COLUMN breaks datemultirange;
--rollback ALTER TABLE core.semester DROP COLUMN breaks;

--changeset aciee:020-professor-profiles
CREATE FUNCTION core.name_key(name text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE
RETURN (SELECT string_agg(token, ' ' ORDER BY token)
        FROM regexp_split_to_table(lower(translate(name, 'ĂÂÎȘŞȚŢăâîșşțţ', 'AAISSTTaaisstt')), '[^a-z]+') token
        WHERE length(token) > 1);
GRANT EXECUTE ON FUNCTION core.name_key(text) TO aciee_ident;

CREATE TABLE core.professor_profile (
    name_key text PRIMARY KEY,
    bio_ro   text NOT NULL CHECK (length(bio_ro) <= 600),
    bio_en   text NOT NULL CHECK (length(bio_en) <= 600),
    photo    text CHECK (photo ~ '^/professors/[a-z0-9-]+\.jpg$'),
    cv_url   text CHECK (cv_url ~ '^https://')
);
--rollback DROP TABLE core.professor_profile; DROP FUNCTION core.name_key(text);
