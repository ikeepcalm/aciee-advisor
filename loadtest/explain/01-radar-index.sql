SELECT teaching_assignment_id AS ta FROM anon.rating GROUP BY 1 ORDER BY count(*) DESC LIMIT 1 \gset
\set radar 'SELECT count(*), round(avg(a01), 1), round(avg(a02), 1), round(avg(a03), 1), round(avg(a04), 1), round(avg(a05), 1), round(avg(a06), 1), round(avg(a07), 1), round(avg(a08), 1), round(avg(a09), 1), round(avg(a10), 1), round(avg(a11), 1), round(avg(a12), 1), round(avg(a13), 1), round(avg(a14), 1) FROM anon.rating WHERE teaching_assignment_id = ' :ta

\echo '=== 1a. No index on rating(teaching_assignment_id)'
EXPLAIN (ANALYZE, BUFFERS) :radar;

CREATE INDEX rating_assignment_demo ON anon.rating (teaching_assignment_id);
\echo '=== 1b. B-tree on rating(teaching_assignment_id)'
EXPLAIN (ANALYZE, BUFFERS) :radar;
SELECT pg_size_pretty(pg_relation_size('anon.rating_assignment_demo')) AS plain_index_size;
DROP INDEX anon.rating_assignment_demo;

CREATE INDEX rating_assignment_cover_demo ON anon.rating (teaching_assignment_id)
    INCLUDE (a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14);
VACUUM (ANALYZE) anon.rating;
\echo '=== 1c. Covering index INCLUDE (a01..a14) after VACUUM: index-only scan'
EXPLAIN (ANALYZE, BUFFERS) :radar;
SELECT pg_size_pretty(pg_relation_size('anon.rating_assignment_cover_demo')) AS covering_index_size;
DROP INDEX anon.rating_assignment_cover_demo;
