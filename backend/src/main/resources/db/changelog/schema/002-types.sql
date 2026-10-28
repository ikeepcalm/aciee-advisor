--liquibase formatted sql

--changeset aciee:002-types
CREATE SCHEMA ref;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE COLLATION core.ro_ro (provider = icu, locale = 'ro-RO');

CREATE DOMAIN ref.score AS smallint CHECK (VALUE BETWEEN 0 AND 10);
CREATE DOMAIN ref.pct AS smallint CHECK (VALUE BETWEEN 0 AND 100);
CREATE DOMAIN ref.sha256 AS bytea CHECK (octet_length(VALUE) = 32);
CREATE DOMAIN ref.week_start AS date CHECK (extract(isodow FROM VALUE) = 1);
--rollback DROP DOMAIN ref.week_start; DROP DOMAIN ref.sha256; DROP DOMAIN ref.pct; DROP DOMAIN ref.score;
--rollback DROP COLLATION core.ro_ro; DROP EXTENSION btree_gist; DROP SCHEMA ref;
