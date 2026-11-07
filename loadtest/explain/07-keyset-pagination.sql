SELECT count(*) / 2 AS skip FROM anon.post_version WHERE status = 'pending' \gset
SELECT submitted_week AS w, post_id AS p, version_no AS v FROM anon.post_version WHERE status = 'pending'
ORDER BY submitted_week, post_id, version_no OFFSET :skip - 1 LIMIT 1 \gset

\echo '=== 7a. OFFSET: reads and throws away every skipped row'
EXPLAIN (ANALYZE, BUFFERS)
SELECT post_id, version_no, submitted_week FROM anon.post_version WHERE status = 'pending'
ORDER BY submitted_week, post_id, version_no OFFSET :skip LIMIT 20;

\echo '=== 7b. Keyset: starts right after the last row of the previous page'
EXPLAIN (ANALYZE, BUFFERS)
SELECT post_id, version_no, submitted_week FROM anon.post_version WHERE status = 'pending'
  AND (submitted_week, post_id, version_no) > (:'w', :'p', :v)
ORDER BY submitted_week, post_id, version_no LIMIT 20;
