SELECT table_name AS tn, row_id AS rid FROM core.audit_log ORDER BY created_at DESC LIMIT 1 \gset
SELECT date_trunc('month', max(created_at)) AS month_start FROM core.audit_log \gset

\echo '=== 4a. History of one row (Q13): local (table_name, row_id) index on each partition'
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, created_at, op FROM core.audit_log WHERE table_name = :'tn' AND row_id = :rid ORDER BY id DESC;

\echo '=== 4b. One month, paged (Q14), literal range: plan-time pruning + BRIN'
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, created_at, table_name, row_id, op FROM core.audit_log
WHERE created_at >= :'month_start' AND created_at < timestamptz :'month_start' + interval '7 days'
ORDER BY id DESC LIMIT 50;

\echo '=== 4c. Same query as a generic prepared plan: run-time pruning (Subplans Removed)'
PREPARE audit_range(timestamptz, timestamptz) AS
    SELECT id, created_at, table_name, row_id, op FROM core.audit_log
    WHERE created_at >= $1 AND created_at < $2 ORDER BY id DESC LIMIT 50;
SET plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS) EXECUTE audit_range(:'month_start', timestamptz :'month_start' + interval '7 days');
RESET plan_cache_mode;

\echo '=== 4d. BRIN vs B-tree on created_at, summed over all partitions'
SELECT pg_size_pretty(sum(pg_relation_size(relid))) AS brin_size FROM pg_partition_tree('core.audit_log_created_brin');
BEGIN;
CREATE INDEX audit_log_created_btree_demo ON core.audit_log (created_at);
SELECT pg_size_pretty(sum(pg_relation_size(relid))) AS btree_size FROM pg_partition_tree('core.audit_log_created_btree_demo');
ROLLBACK;

\echo '=== 4e0. The same 10k-row update with no audit trigger (baseline)'
BEGIN;
ALTER TABLE core.teaching_assignment DISABLE TRIGGER audit_update;
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)
UPDATE core.teaching_assignment SET valid_during = valid_during WHERE id IN (SELECT id FROM core.teaching_assignment ORDER BY id LIMIT 10000);
ROLLBACK;

\echo '=== 4e. Statement-level audit trigger (in the schema) on a 10k-row update'
BEGIN;
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)
UPDATE core.teaching_assignment SET valid_during = valid_during WHERE id IN (SELECT id FROM core.teaching_assignment ORDER BY id LIMIT 10000);
ROLLBACK;

\echo '=== 4f. Same update with a per-row audit trigger instead'
BEGIN;
ALTER TABLE core.teaching_assignment DISABLE TRIGGER audit_update;
CREATE FUNCTION core.audit_row_demo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO core.audit_log (actor_user_id, table_name, row_id, op, old, new)
    VALUES (nullif(current_setting('app.user_id', true), '')::integer, TG_TABLE_NAME, NEW.id, TG_OP, to_jsonb(OLD), to_jsonb(NEW));
    RETURN NULL;
END $$;
CREATE TRIGGER audit_update_row_demo AFTER UPDATE ON core.teaching_assignment FOR EACH ROW EXECUTE FUNCTION core.audit_row_demo();
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)
UPDATE core.teaching_assignment SET valid_during = valid_during WHERE id IN (SELECT id FROM core.teaching_assignment ORDER BY id LIMIT 10000);
ROLLBACK;
