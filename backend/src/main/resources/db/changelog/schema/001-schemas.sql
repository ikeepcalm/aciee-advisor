--liquibase formatted sql

--changeset aciee:001-schemas
CREATE SCHEMA core;
CREATE SCHEMA ident;
CREATE SCHEMA anon;
--rollback DROP SCHEMA anon; DROP SCHEMA ident; DROP SCHEMA core;
