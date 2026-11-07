--liquibase formatted sql

--changeset aciee:018-published-post-lateral
CREATE OR REPLACE VIEW anon.published_post AS
SELECT p.id, p.offering_id, p.kind, p.teaching_assignment_id, v.version_no, v.body, v.moderated_week
FROM anon.post p
CROSS JOIN LATERAL (
    SELECT pv.version_no, pv.body, pv.moderated_week
    FROM anon.post_version pv
    WHERE pv.post_id = p.id AND pv.status = 'approved'
    ORDER BY pv.version_no DESC
    LIMIT 1) v
WHERE p.kind = 'tip' OR EXISTS (
        SELECT 1
        FROM core.teaching_assignment ta
        JOIN core.course_offering o ON o.id = ta.offering_id
        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
        JOIN anon.prof_subject_radar r ON r.professor_id = ta.professor_id AND r.subject_id = sr.root_id
        CROSS JOIN ref.setting s
        WHERE ta.id = p.teaching_assignment_id AND r.n >= s.k_threshold
        -- OFFSET 0 keeps this a per-row subplan; without it the planner hashes the k check over every assignment
        OFFSET 0);
--rollback CREATE OR REPLACE VIEW anon.published_post AS
--rollback SELECT DISTINCT ON (p.id) p.id, p.offering_id, p.kind, p.teaching_assignment_id, v.version_no, v.body, v.moderated_week
--rollback FROM anon.post p JOIN anon.post_version v ON v.post_id = p.id
--rollback WHERE v.status = 'approved' AND (p.kind = 'tip' OR EXISTS (SELECT 1 FROM core.teaching_assignment ta
--rollback JOIN core.course_offering o ON o.id = ta.offering_id JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
--rollback JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
--rollback JOIN anon.prof_subject_radar r ON r.professor_id = ta.professor_id AND r.subject_id = sr.root_id
--rollback CROSS JOIN ref.setting s WHERE ta.id = p.teaching_assignment_id AND r.n >= s.k_threshold))
--rollback ORDER BY p.id, v.version_no DESC;

--changeset aciee:018-no-jit-on-request-roles
ALTER ROLE aciee_ident SET jit = off;
ALTER ROLE aciee_anon SET jit = off;
--rollback ALTER ROLE aciee_ident RESET jit; ALTER ROLE aciee_anon RESET jit;
