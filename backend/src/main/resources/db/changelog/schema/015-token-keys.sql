--liquibase formatted sql

--changeset aciee:015-token-keys
GRANT USAGE ON SCHEMA anon TO aciee_ident;
GRANT SELECT, INSERT ON anon.offering_key TO aciee_ident;
--rollback REVOKE SELECT, INSERT ON anon.offering_key FROM aciee_ident; REVOKE USAGE ON SCHEMA anon FROM aciee_ident;
