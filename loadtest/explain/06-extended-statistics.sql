SELECT teaching_assignment_id AS ta, offering_id AS off FROM anon.rating
GROUP BY 1, 2 ORDER BY count(*) DESC LIMIT 1 \gset
\set pair 'SELECT count(*) FROM anon.rating WHERE teaching_assignment_id = ' :ta ' AND offering_id = ' :off

BEGIN;
DROP STATISTICS anon.rating_assignment_offering;
ANALYZE anon.rating;
\echo '=== 6a. Without CREATE STATISTICS: the planner multiplies both selectivities'
EXPLAIN (ANALYZE) :pair;
ROLLBACK;

ANALYZE anon.rating;
\echo '=== 6b. With rating_assignment_offering (dependencies): assignment determines offering'
EXPLAIN (ANALYZE) :pair;
SELECT statistics_name, dependencies FROM pg_stats_ext WHERE statistics_name = 'rating_assignment_offering';
