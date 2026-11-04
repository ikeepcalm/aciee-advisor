--liquibase formatted sql

--changeset aciee:016-ident-on-anon
GRANT SELECT, INSERT, UPDATE, DELETE ON anon.submission, anon.rating, anon.post, anon.post_version, anon.post_report TO aciee_ident;
GRANT SELECT ON anon.prof_subject_radar, anon.course_survival, anon.published_post, anon.moderation_queue TO aciee_ident;
GRANT EXECUTE ON FUNCTION anon.refresh_aggregates() TO aciee_ident;
--rollback REVOKE ALL ON anon.submission, anon.rating, anon.post, anon.post_version, anon.post_report FROM aciee_ident;
--rollback REVOKE ALL ON anon.prof_subject_radar, anon.course_survival, anon.published_post, anon.moderation_queue FROM aciee_ident;
--rollback REVOKE EXECUTE ON FUNCTION anon.refresh_aggregates() FROM aciee_ident;

--changeset aciee:016-publish-on-approval
CREATE OR REPLACE VIEW anon.published_post AS
SELECT DISTINCT ON (p.id)
       p.id, p.offering_id, p.kind, p.teaching_assignment_id, v.version_no, v.body, v.moderated_week
FROM anon.post p
JOIN anon.post_version v ON v.post_id = p.id
WHERE v.status = 'approved'
  AND (p.kind = 'tip' OR EXISTS (
        SELECT 1
        FROM core.teaching_assignment ta
        JOIN core.course_offering o ON o.id = ta.offering_id
        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
        JOIN anon.prof_subject_radar r ON r.professor_id = ta.professor_id AND r.subject_id = sr.root_id
        CROSS JOIN ref.setting s
        WHERE ta.id = p.teaching_assignment_id AND r.n >= s.k_threshold))
ORDER BY p.id, v.version_no DESC;
--rollback SELECT 1;
