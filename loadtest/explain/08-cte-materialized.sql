SELECT professor_id AS pid FROM core.teaching_assignment GROUP BY 1 ORDER BY count(*) DESC LIMIT 1 \gset

\echo '=== 8a. Professor page, taught subjects CTE inlined into both branches'
EXPLAIN (ANALYZE, BUFFERS)
WITH taught AS NOT MATERIALIZED (
    SELECT DISTINCT sr.root_id
    FROM core.teaching_assignment ta
    JOIN core.course_offering o ON o.id = ta.offering_id
    JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
    JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
    WHERE ta.professor_id = :pid)
SELECT 'radar' AS part, count(*) FROM anon.prof_subject_radar r
WHERE r.professor_id = :pid AND r.subject_id IN (SELECT root_id FROM taught)
UNION ALL
SELECT 'assignments', count(*) FROM core.teaching_assignment ta
JOIN core.course_offering o ON o.id = ta.offering_id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
WHERE ta.professor_id = :pid AND sr.root_id IN (SELECT root_id FROM taught);

\echo '=== 8b. Same query, CTE MATERIALIZED: computed once, read twice'
EXPLAIN (ANALYZE, BUFFERS)
WITH taught AS MATERIALIZED (
    SELECT DISTINCT sr.root_id
    FROM core.teaching_assignment ta
    JOIN core.course_offering o ON o.id = ta.offering_id
    JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
    JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
    WHERE ta.professor_id = :pid)
SELECT 'radar' AS part, count(*) FROM anon.prof_subject_radar r
WHERE r.professor_id = :pid AND r.subject_id IN (SELECT root_id FROM taught)
UNION ALL
SELECT 'assignments', count(*) FROM core.teaching_assignment ta
JOIN core.course_offering o ON o.id = ta.offering_id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
WHERE ta.professor_id = :pid AND sr.root_id IN (SELECT root_id FROM taught);
