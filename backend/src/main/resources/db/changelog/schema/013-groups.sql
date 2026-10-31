--liquibase formatted sql

--changeset aciee:013-groups
CREATE INDEX group_membership_group_idx ON core.group_membership (group_id);
CREATE UNIQUE INDEX group_leader_claim_one_pending_idx ON core.group_leader_claim (user_id, group_id) WHERE status = 'pending';

CREATE FUNCTION core.invite_uses(target_invite integer) RETURNS bigint
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
RETURN (SELECT count(*) FROM core.membership_request r WHERE r.invite_id = target_invite AND r.status <> 'rejected');

GRANT EXECUTE ON FUNCTION core.invite_uses(integer) TO aciee_ident;

CREATE VIEW core.group_v AS
SELECT g.id,
       g.code,
       g.plan_id,
       g.academic_year_id,
       g.study_year,
       g.max_size,
       sp.name_ro AS specialization,
       ay.during AS year_during,
       extract(year FROM lower(ay.during))::int || '–' || extract(year FROM upper(ay.during))::int AS academic_year
FROM core.study_group g
JOIN core.study_plan p ON p.id = g.plan_id
JOIN core.specialization sp ON sp.id = p.specialization_id
JOIN core.academic_year ay ON ay.id = g.academic_year_id;
--rollback DROP VIEW core.group_v; DROP FUNCTION core.invite_uses(integer);
--rollback DROP INDEX core.group_leader_claim_one_pending_idx; DROP INDEX core.group_membership_group_idx;
