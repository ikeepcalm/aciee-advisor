--liquibase formatted sql

--changeset aciee:025-cohort-history
ALTER TABLE core.study_group ADD COLUMN successor_id integer REFERENCES core.study_group;
CREATE INDEX study_group_successor_idx ON core.study_group (successor_id);
--rollback DROP INDEX core.study_group_successor_idx; ALTER TABLE core.study_group DROP COLUMN successor_id;
