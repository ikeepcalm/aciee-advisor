--liquibase formatted sql

--changeset aciee:fixtures-audit-partitions context:fixtures,fixtures-large splitStatements:false
DO $$
DECLARE
    month date;
BEGIN
    FOR month IN
        SELECT generate_series(date_trunc('month', now() AT TIME ZONE 'UTC') - interval '12 months',
                               date_trunc('month', now() AT TIME ZONE 'UTC'), interval '1 month')::date
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS core.%I PARTITION OF core.audit_log FOR VALUES FROM (%L) TO (%L)',
                       'audit_log_' || to_char(month, 'YYYY_MM'),
                       month || ' 00:00 UTC',
                       (month + interval '1 month')::date || ' 00:00 UTC');
    END LOOP;
END
$$;
--rollback SELECT 1;

--changeset aciee:fixtures-synthetic context:fixtures,fixtures-large
SELECT setseed(0.42);

CREATE TEMP TABLE fx ON COMMIT DROP AS
SELECT scale, specs, greatest(1, round(240 * scale / specs))::int AS per, greatest(12, round(600 * scale))::int AS professors,
       extract(year FROM current_date - interval '9 months')::int AS cy
FROM (SELECT scale, greatest(1, round(24 * scale))::int AS specs FROM (VALUES (${fixture_scale}::numeric)) v(scale)) s;

CREATE FUNCTION pg_temp.score(chance float8) RETURNS smallint
LANGUAGE sql VOLATILE
RETURN CASE WHEN random() < chance THEN (1 + floor(random() * 10))::smallint END;

INSERT INTO core.academic_year (during)
SELECT daterange(make_date(y, 10, 1), make_date(y + 1, 10, 1))
FROM fx, generate_series(fx.cy - 5, fx.cy) y
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE fx_year ON COMMIT DROP AS
SELECT y, a.id, a.during
FROM fx, generate_series(fx.cy - 5, fx.cy) y
JOIN core.academic_year a ON a.during @> make_date(y, 10, 15);

INSERT INTO core.semester (academic_year_id, no, during, rating_opens_at)
SELECT y.id, n,
       CASE n WHEN 1 THEN daterange(make_date(y.y, 10, 1), make_date(y.y + 1, 2, 15))
              ELSE daterange(make_date(y.y + 1, 2, 24), make_date(y.y + 1, 7, 1)) END,
       CASE n WHEN 1 THEN make_date(y.y + 1, 1, 12) ELSE make_date(y.y + 1, 6, 1) END
FROM fx_year y CROSS JOIN generate_series(1, 2) n
ON CONFLICT DO NOTHING;

INSERT INTO core.faculty (code, name_ro) VALUES ('FIX', 'Facultate sintetică');
INSERT INTO core.department (faculty_id, name_ro)
SELECT f.id, 'Departament sintetic ' || d FROM core.faculty f CROSS JOIN generate_series(1, 4) d WHERE f.code = 'FIX';
INSERT INTO core.specialization (faculty_id, code, name_ro, degree, duration_years)
SELECT f.id, 'FX' || lpad(s::text, 2, '0'), 'Specializare sintetică ' || s, 'BSc', 4
FROM core.faculty f, fx, generate_series(1, fx.specs) s
WHERE f.code = 'FIX';
INSERT INTO core.study_plan (specialization_id, intake)
SELECT id, '[2000,)' FROM core.specialization WHERE code LIKE 'FX__';

INSERT INTO core.subject (code, name_ro)
SELECT sp.code || '.' || y || s || n, 'Disciplina ' || sp.code || ' ' || y || '.' || s || '.' || n
FROM core.specialization sp, generate_series(1, 4) y, generate_series(1, 2) s, generate_series(1, 6) n
WHERE sp.code LIKE 'FX__';
INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no, lecture_hours, seminar_hours, lab_hours, project_hours)
SELECT p.id, sub.id, y, s, 2, (n % 3 = 0)::int, 2 * (n % 3 <> 0)::int, (n = 6)::int
FROM core.specialization sp
JOIN core.study_plan p ON p.specialization_id = sp.id
CROSS JOIN generate_series(1, 4) y CROSS JOIN generate_series(1, 2) s CROSS JOIN generate_series(1, 6) n
JOIN core.subject sub ON sub.code = sp.code || '.' || y || s || n
WHERE sp.code LIKE 'FX__';

INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
SELECT p.id, y.id, sy, sp.code || '-' || sy || lpad(k::text, 2, '0'), 32
FROM fx
CROSS JOIN fx_year y
CROSS JOIN core.specialization sp
JOIN core.study_plan p ON p.specialization_id = sp.id
CROSS JOIN generate_series(1, 4) sy
CROSS JOIN generate_series(1, fx.per) k
WHERE sp.code LIKE 'FX__';

CREATE TEMP TABLE fx_group ON COMMIT DROP AS
SELECT g.id, g.plan_id, g.study_year, y.y, y.during, substr(g.code, 3, 2)::int AS sp, substr(g.code, 7, 2)::int AS k
FROM core.study_group g JOIN fx_year y ON y.id = g.academic_year_id
WHERE g.code LIKE 'FX__-%';

INSERT INTO core.subgroup (group_id, label) SELECT g.id, l::text FROM fx_group g CROSS JOIN generate_series(1, 2) l;

INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
SELECT ci.id, g.id, g.plan_id, g.study_year, s.name_ro
FROM fx_group g
JOIN core.curriculum_item ci ON ci.plan_id = g.plan_id AND ci.study_year = g.study_year
JOIN core.subject s ON s.id = ci.subject_id;

INSERT INTO core.professor (full_name, title_code, department_id, slug)
SELECT n.first || ' ' || n.last, (ARRAY['prof', 'conf', 'sl', 'lect', 'asist', 'asoc'])[1 + i % 6], d.id,
       lower(n.first || '-' || n.last) || '-fx' || i
FROM fx
CROSS JOIN generate_series(1, fx.professors) i
CROSS JOIN LATERAL (SELECT (ARRAY['Ion', 'Maria', 'Andrei', 'Elena', 'Mihai', 'Ana', 'Gheorghe', 'Ioana', 'Dan', 'Cristina',
                                  'Alexandru', 'Gabriela', 'Florin', 'Mirela', 'Vlad', 'Simona', 'Radu', 'Adriana', 'Bogdan',
                                  'Laura'])[1 + (i - 1) % 20] AS first,
                           (ARRAY['Popescu', 'Ionescu', 'Stan', 'Dumitru', 'Stoica', 'Gheorghiu', 'Rusu', 'Munteanu', 'Matei',
                                  'Constantin', 'Marin', 'Tudor', 'Dobre', 'Barbu', 'Nistor', 'Florea', 'Ene', 'Lazar',
                                  'Moldovan', 'Cristea', 'Toma', 'Sandu', 'Mocanu', 'Ilie', 'Badea'])[1 + ((i - 1) / 20) % 25] AS last) n
JOIN core.faculty f ON f.code = 'FIX'
JOIN core.department d ON d.faculty_id = f.id AND d.name_ro = 'Departament sintetic ' || (1 + i % 4);

CREATE TEMP TABLE fx_professor ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY id) - 1 AS n, id FROM core.professor WHERE slug ~ '-fx[0-9]+$';

INSERT INTO core.teaching_assignment (offering_id, group_id, professor_id, component, subgroup_id, valid_during)
SELECT o.id, g.id, p.id, c.component, s.id, g.during
FROM fx_group g
JOIN core.course_offering o ON o.group_id = g.id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
CROSS JOIN LATERAL (VALUES ('lecture', ci.lecture_hours, 0), ('seminar', ci.seminar_hours, 1),
                           ('lab', ci.lab_hours, 2), ('project', ci.project_hours, 3)) c(component, hours, shift)
LEFT JOIN core.subgroup s ON s.group_id = g.id AND c.component <> 'lecture'
JOIN fx_professor p ON p.n = (ci.subject_id * 7 + c.shift * 3 + coalesce(s.label::int, 0)) % (SELECT count(*) FROM fx_professor)
WHERE c.hours > 0;

CREATE TEMP TABLE fx_user ON COMMIT DROP AS
SELECT row_number() OVER () AS n, intake, sp, k, seat, NULL::integer AS id
FROM fx, generate_series(fx.cy - 8, fx.cy) intake, generate_series(1, fx.specs) sp, generate_series(1, fx.per) k,
     generate_series(1, 28) seat;

INSERT INTO core.user_account (email, password_hash, created_at)
SELECT 'fx' || n || '@student.ugal.ro',
       replace('#argon2id#v=19#m=16384,t=2,p=1#85g/yriKoiZ2V8IUnMDQjA#znWcYrjUtuAgs9B/dsrYG8rX+bHSi3Vi+WSf3IDh+HE', '#', chr(36)),
       make_date(intake, 9, 20)
FROM fx_user
UNION ALL
SELECT 'demo.' || r || '@student.ugal.ro',
       replace('#argon2id#v=19#m=16384,t=2,p=1#85g/yriKoiZ2V8IUnMDQjA#znWcYrjUtuAgs9B/dsrYG8rX+bHSi3Vi+WSf3IDh+HE', '#', chr(36)),
       now()
FROM unnest(ARRAY['admin', 'moderator', 'leader', 'student']) r;
UPDATE fx_user u SET id = a.id FROM core.user_account a WHERE a.email = 'fx' || u.n || '@student.ugal.ro';
INSERT INTO core.user_role (user_id, role_code)
SELECT id, substr(email, 6, strpos(email, '@') - 6) FROM core.user_account
WHERE email IN ('demo.admin@student.ugal.ro', 'demo.moderator@student.ugal.ro');

INSERT INTO core.group_membership (user_id, group_id, subgroup_id, kind, source, valid_during)
SELECT u.id, g.id, s.id, 'regular', 'university_sync', g.during
FROM fx
CROSS JOIN fx_user u
JOIN fx_group g ON g.sp = u.sp AND g.k = u.k AND g.study_year = g.y - u.intake + 1
JOIN core.subgroup s ON s.group_id = g.id AND s.label = (1 + u.seat % 2)::text
WHERE NOT (g.y = fx.cy AND g.study_year = 1 AND u.seat % 10 = 0)
UNION ALL
SELECT a.id, g.id, s.id, 'regular', 'university_sync', g.during
FROM fx
JOIN fx_group g ON g.y = fx.cy AND g.sp = 1 AND g.study_year = 3 AND g.k = 1
JOIN core.subgroup s ON s.group_id = g.id AND s.label = '1'
CROSS JOIN core.user_account a
WHERE a.email IN ('demo.leader@student.ugal.ro', 'demo.student@student.ugal.ro');

INSERT INTO core.membership_request (user_id, group_id, subgroup_id, kind)
SELECT u.id, g.id, s.id, 'regular'
FROM fx
CROSS JOIN fx_user u
JOIN fx_group g ON g.sp = u.sp AND g.k = u.k AND g.y = fx.cy AND g.study_year = 1 AND u.intake = fx.cy
JOIN core.subgroup s ON s.group_id = g.id AND s.label = (1 + u.seat % 2)::text
WHERE u.seat % 10 = 0;

INSERT INTO core.group_leader_claim (user_id, group_id, status, decided_by, decided_at)
SELECT m.user_id, m.group_id, 'approved', (SELECT id FROM core.user_account WHERE email = 'demo.admin@student.ugal.ro'), now()
FROM fx
CROSS JOIN core.group_membership m
JOIN fx_group g ON g.id = m.group_id AND g.y = fx.cy
JOIN core.user_account a ON a.id = m.user_id
LEFT JOIN fx_user u ON u.id = m.user_id
WHERE u.seat = 1 OR a.email = 'demo.leader@student.ugal.ro';
INSERT INTO core.group_leader (membership_id, claim_id)
SELECT m.id, c.id
FROM core.group_leader_claim c
JOIN fx_group g ON g.id = c.group_id
JOIN core.group_membership m ON m.user_id = c.user_id AND m.group_id = c.group_id;

INSERT INTO anon.offering_key (fingerprint, offering_id, public_key, active)
SELECT sha256(convert_to('fx-key-' || o.id, 'UTF8')), o.id, sha256(convert_to('fx-public-' || o.id, 'UTF8')), false
FROM fx_group g JOIN core.course_offering o ON o.group_id = g.id;

CREATE TEMP TABLE fx_submission ON COMMIT DROP AS
SELECT sha256(convert_to('fx-' || m.user_id || '-' || o.id, 'UTF8')) AS key_hash, o.id AS offering_id, m.subgroup_id
FROM fx_group g
JOIN core.group_membership m ON m.group_id = g.id
JOIN core.course_offering o ON o.group_id = g.id
WHERE random() < 0.75;

INSERT INTO anon.submission (key_hash, offering_id, key_fingerprint, exam_difficulty, labs_help, attendance_useful,
                             weekly_hours, exam_format, attendance_strictness)
SELECT key_hash, offering_id, sha256(convert_to('fx-key-' || offering_id, 'UTF8')), pg_temp.score(0.9), pg_temp.score(0.9),
       (ARRAY['yes', 'partly', 'no'])[1 + floor(random() * 3)::int], floor(random() * 16)::smallint,
       (ARRAY['theory', 'practical', 'oral', 'mixed'])[1 + floor(random() * 4)::int], pg_temp.score(0.9)
FROM fx_submission;

INSERT INTO anon.rating (key_hash, teaching_assignment_id, offering_id,
                         a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14)
SELECT s.key_hash, ta.id, s.offering_id, pg_temp.score(1), pg_temp.score(0.8), pg_temp.score(0.8), pg_temp.score(0.8),
       pg_temp.score(0.8), pg_temp.score(0.8), pg_temp.score(0.8), pg_temp.score(0.8), pg_temp.score(0.8),
       pg_temp.score(0.8), pg_temp.score(0.8), pg_temp.score(0.8), pg_temp.score(0.8), pg_temp.score(0.8)
FROM fx_submission s
JOIN core.teaching_assignment ta ON ta.offering_id = s.offering_id AND (ta.subgroup_id IS NULL OR ta.subgroup_id = s.subgroup_id);

INSERT INTO anon.post (key_hash, offering_id, kind)
SELECT key_hash, offering_id, 'tip' FROM fx_submission WHERE get_byte(key_hash, 0) < 26;
INSERT INTO anon.post (key_hash, offering_id, kind, teaching_assignment_id)
SELECT r.key_hash, r.offering_id, 'comment', r.teaching_assignment_id
FROM anon.rating r
WHERE (get_byte(r.key_hash, 1) + r.teaching_assignment_id) % 20 = 0;

INSERT INTO anon.post_version (post_id, version_no, kind, body, status, reason_code, submitted_week, moderated_week)
SELECT p.id, v.no, p.kind,
       CASE p.kind WHEN 'tip' THEN 'Sfat sintetic: începe proiectul din primele săptămâni.' ELSE 'Comentariu sintetic.' END,
       v.status, CASE v.status WHEN 'rejected' THEN 'off_topic' END, v.week, CASE WHEN v.status <> 'pending' THEN v.week + 7 END
FROM anon.post p
CROSS JOIN LATERAL (
    SELECT no,
           CASE WHEN no < versions THEN 'approved'
                WHEN get_byte(p.key_hash, 2) < 26 THEN 'pending'
                WHEN get_byte(p.key_hash, 2) < 38 THEN 'rejected'
                ELSE 'approved' END AS status,
           date_trunc('week', current_date)::date - 7 * (1 + versions - no + (get_byte(p.key_hash, 3) % 50) * (get_byte(p.key_hash, 2) >= 26)::int) AS week
    FROM (SELECT 1 + (get_byte(p.key_hash, 4) < 51)::int AS versions) c
    CROSS JOIN LATERAL generate_series(1, c.versions) no
) v;

INSERT INTO anon.post_report (post_id, reason_code, reported_week)
SELECT p.id, 'insult', date_trunc('week', current_date)::date
FROM anon.post p
WHERE get_byte(p.key_hash, 5) < 3;

INSERT INTO core.audit_log (created_at, table_name, row_id, op, old, new)
SELECT now() - interval '350 days' * (1 - n.i::float8 / n.total), 'membership_request', floor(random() * 100000)::int,
       'UPDATE', '{"status": "pending"}', '{"status": "approved"}'
FROM (SELECT i, count(*) OVER () AS total
      FROM fx, generate_series(1, greatest(0, round(5000000 * fx.scale)::int - (SELECT count(*) FROM core.audit_log)::int)) i) n
ORDER BY n.i;

ANALYZE anon.submission, anon.rating, core.teaching_assignment, core.course_offering, core.curriculum_item;

REFRESH MATERIALIZED VIEW anon.prof_subject_radar;
REFRESH MATERIALIZED VIEW anon.course_survival;
--rollback SELECT 1;

--changeset aciee:fixtures-analyze context:fixtures,fixtures-large runInTransaction:false runAlways:false
VACUUM ANALYZE;
--rollback SELECT 1;
