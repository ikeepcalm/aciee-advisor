SELECT m.user_id AS uid FROM core.group_membership m
JOIN core.course_offering o ON o.group_id = m.group_id
GROUP BY m.user_id ORDER BY count(*) DESC LIMIT 1 \gset
SELECT set_config('app.user_id', :'uid', false);

BEGIN;
INSERT INTO ident.token_issuance (user_id, offering_id, issued_week, blinded_msg, blind_sig)
SELECT :uid, o.id, date_trunc('week', current_date)::date, '\x00', '\x00'
FROM core.group_membership m JOIN core.course_offering o ON o.group_id = m.group_id
WHERE m.user_id = :uid AND o.id % 2 = 0;
ANALYZE ident.token_issuance;

\echo '=== 3a. The /rate list as the app runs it (TokenService.OFFERINGS): memberships through the temporal key'
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.offering_id, o.title_snapshot AS title, g.academic_year, o.semester_no,
       o.rating_opens_at AS opens_at, coalesce(o.rating_opens_at <= now(), false) AS open, k.fingerprint,
       EXISTS (SELECT 1 FROM ident.token_issuance t WHERE t.user_id = core.app_user_id() AND t.offering_id = o.offering_id) AS issued
FROM core.offering_v o
JOIN core.group_v g ON g.id = o.group_id
LEFT JOIN anon.offering_key k ON k.offering_id = o.offering_id AND k.active
WHERE EXISTS (SELECT 1 FROM core.group_membership m WHERE m.user_id = core.app_user_id() AND m.group_id = o.group_id)
   OR EXISTS (SELECT 1 FROM core.offering_enrolment e WHERE e.user_id = core.app_user_id() AND e.offering_id = o.offering_id)
ORDER BY o.rating_opens_at NULLS LAST, o.semester_no, o.title_snapshot, o.offering_id;

\echo '=== 3b. Not yet issued: NOT EXISTS (anti join)'
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id FROM core.group_membership m JOIN core.course_offering o ON o.group_id = m.group_id
WHERE m.user_id = :uid
  AND NOT EXISTS (SELECT 1 FROM ident.token_issuance t WHERE t.user_id = :uid AND t.offering_id = o.id);

\echo '=== 3c. Not yet issued: NOT IN (hashed subplan, no anti join)'
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id FROM core.group_membership m JOIN core.course_offering o ON o.group_id = m.group_id
WHERE m.user_id = :uid
  AND o.id NOT IN (SELECT t.offering_id FROM ident.token_issuance t WHERE t.user_id = :uid);

\echo '=== 3d. Not yet issued: LEFT JOIN ... IS NULL (same anti join as NOT EXISTS)'
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id FROM core.group_membership m JOIN core.course_offering o ON o.group_id = m.group_id
LEFT JOIN ident.token_issuance t ON t.user_id = :uid AND t.offering_id = o.id
WHERE m.user_id = :uid AND t.user_id IS NULL;

ROLLBACK;

\echo '=== 3e. Why never NOT IN: one NULL in the subquery empties the result'
SELECT count(*) AS not_in_with_null FROM (VALUES (1), (2)) v(x) WHERE x NOT IN (SELECT NULL::integer);
SELECT count(*) AS not_exists_with_null FROM (VALUES (1), (2)) v(x) WHERE NOT EXISTS (SELECT 1 FROM (SELECT NULL::integer AS y) n WHERE n.y = v.x);
