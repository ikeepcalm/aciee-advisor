--liquibase formatted sql

--changeset aciee:023-timetable-subgroups
CREATE TEMP TABLE split_group ON COMMIT DROP AS
SELECT g.id
FROM core.study_group g
JOIN core.academic_year ay ON ay.id = g.academic_year_id
WHERE ay.during @> current_date
  AND g.code ~ '[0-9][A-Z]$'
  AND NOT EXISTS (SELECT 1 FROM anon.submission s JOIN core.course_offering o ON o.id = s.offering_id WHERE o.group_id = g.id);

DELETE FROM core.group_leader l
WHERE l.membership_id IN (SELECT m.id FROM core.group_membership m WHERE m.group_id IN (SELECT id FROM split_group))
   OR l.claim_id IN (SELECT c.id FROM core.group_leader_claim c WHERE c.group_id IN (SELECT id FROM split_group));
DELETE FROM core.offering_enrolment e
WHERE e.offering_id IN (SELECT o.id FROM core.course_offering o WHERE o.group_id IN (SELECT id FROM split_group))
   OR e.request_id IN (SELECT r.id FROM core.membership_request r WHERE r.group_id IN (SELECT id FROM split_group));
DELETE FROM core.group_membership WHERE group_id IN (SELECT id FROM split_group);
DELETE FROM core.group_leader_claim WHERE group_id IN (SELECT id FROM split_group);
DELETE FROM core.membership_request WHERE group_id IN (SELECT id FROM split_group);
DELETE FROM core.group_invite WHERE group_id IN (SELECT id FROM split_group);
DELETE FROM ident.token_issuance WHERE offering_id IN (SELECT o.id FROM core.course_offering o WHERE o.group_id IN (SELECT id FROM split_group));
DELETE FROM anon.offering_key WHERE offering_id IN (SELECT o.id FROM core.course_offering o WHERE o.group_id IN (SELECT id FROM split_group));
DELETE FROM core.teaching_assignment WHERE group_id IN (SELECT id FROM split_group);
DELETE FROM core.course_offering WHERE group_id IN (SELECT id FROM split_group);
DELETE FROM core.subgroup WHERE group_id IN (SELECT id FROM split_group);
DELETE FROM core.study_group WHERE id IN (SELECT id FROM split_group);

ALTER TABLE core.timetable_import DROP CONSTRAINT timetable_import_pkey;
ALTER TABLE core.timetable_import ADD COLUMN subgroup_id integer REFERENCES core.subgroup ON DELETE CASCADE;
ALTER TABLE core.timetable_import ADD CONSTRAINT timetable_import_slot_key UNIQUE NULLS NOT DISTINCT (offering_id, component, subgroup_id);
--rollback ALTER TABLE core.timetable_import DROP CONSTRAINT timetable_import_slot_key; ALTER TABLE core.timetable_import DROP COLUMN subgroup_id; ALTER TABLE core.timetable_import ADD PRIMARY KEY (offering_id, component);
