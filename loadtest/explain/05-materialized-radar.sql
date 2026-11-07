SELECT professor_id AS pid, subject_id AS sid FROM anon.prof_subject_radar ORDER BY n DESC LIMIT 1 \gset

\echo '=== 5a. Radar from the materialised view (Q4): one unique-index lookup'
EXPLAIN (ANALYZE, BUFFERS)
SELECT n, a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14
FROM anon.prof_subject_radar WHERE professor_id = :pid AND subject_id = :sid;

\echo '=== 5b. The same radar computed live from anon.rating_v'
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(DISTINCT key_hash) AS n, round(avg(a01), 1), round(avg(a02), 1), round(avg(a03), 1), round(avg(a04), 1),
       round(avg(a05), 1), round(avg(a06), 1), round(avg(a07), 1), round(avg(a08), 1), round(avg(a09), 1),
       round(avg(a10), 1), round(avg(a11), 1), round(avg(a12), 1), round(avg(a13), 1), round(avg(a14), 1)
FROM anon.rating_v WHERE professor_id = :pid AND subject_id = :sid;

\echo '=== 5c. Cost of one concurrent refresh of both views'
\timing on
SELECT anon.refresh_aggregates();
\timing off
