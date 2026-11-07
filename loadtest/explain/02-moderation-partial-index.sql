\set queue 'SELECT post_id, version_no, submitted_week FROM anon.post_version WHERE status = ''pending'' ORDER BY submitted_week, post_id, version_no LIMIT 20'
SELECT count(*) AS pending, (SELECT count(*) FROM anon.post_version) AS versions FROM anon.post_version WHERE status = 'pending';

\echo '=== 2a. Partial index post_version_queue_idx (in the schema)'
EXPLAIN (ANALYZE, BUFFERS) :queue;

BEGIN;
DROP INDEX anon.post_version_queue_idx;
\echo '=== 2b. Without it'
EXPLAIN (ANALYZE, BUFFERS) :queue;
CREATE INDEX post_version_status_demo ON anon.post_version (status, submitted_week, post_id, version_no);
\echo '=== 2c. Full index (status, submitted_week, post_id, version_no)'
EXPLAIN (ANALYZE, BUFFERS) :queue;
SELECT pg_size_pretty(pg_relation_size('anon.post_version_status_demo')) AS full_index_size;
ROLLBACK;
SELECT pg_size_pretty(pg_relation_size('anon.post_version_queue_idx')) AS partial_index_size;

\echo '=== 2d. Status as a bind parameter with a generic plan: the partial index cannot be used'
PREPARE queue_by_status(text) AS
    SELECT post_id, version_no, submitted_week FROM anon.post_version WHERE status = $1
    ORDER BY submitted_week, post_id, version_no LIMIT 20;
SET plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS) EXECUTE queue_by_status('pending');
RESET plan_cache_mode;
