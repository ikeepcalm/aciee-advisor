SELECT professor_id AS pid FROM core.teaching_assignment GROUP BY 1 ORDER BY count(*) DESC LIMIT 1 \gset
\set comments 'SELECT ta.professor_id AS key, pp.id, pp.body, pp.moderated_week FROM published_post pp JOIN core.teaching_assignment ta ON ta.id = pp.teaching_assignment_id JOIN core.course_offering o ON o.id = ta.offering_id JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id WHERE pp.kind = ''comment'' AND ta.professor_id = ' :pid ' ORDER BY pp.moderated_week DESC, pp.id'

BEGIN;
CREATE TEMP VIEW published_post AS
SELECT DISTINCT ON (p.id) p.id, p.offering_id, p.kind, p.teaching_assignment_id, v.version_no, v.body, v.moderated_week
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
\echo '=== 9a. Professor page comments over the DISTINCT ON view (changeset 016): the whole view is built first'
EXPLAIN (ANALYZE, BUFFERS) :comments;
ROLLBACK;

SET search_path = anon, public;
\echo '=== 9b. Same query over the LATERAL view (changeset 018): post -> latest approved version by PK, per-row k check'
EXPLAIN (ANALYZE, BUFFERS) :comments;

\echo '=== 9c. Same query with JIT on, as the app roles ran before changeset 018: compile time dominates'
SET jit = on;
EXPLAIN (ANALYZE, BUFFERS) :comments;
RESET jit;
RESET search_path;
