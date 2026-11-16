--liquibase formatted sql

--changeset aciee:021-display-name
ALTER TABLE core.user_account ADD COLUMN display_name text
    CHECK (length(display_name) BETWEEN 1 AND 80 AND display_name !~ '[[:cntrl:]]');
--rollback ALTER TABLE core.user_account DROP COLUMN display_name;
