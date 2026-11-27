--liquibase formatted sql

--changeset aciee:026-exam-options
CREATE TABLE ref.exam_option (
    code     text PRIMARY KEY,
    kind     text NOT NULL CHECK (kind IN ('form', 'content')),
    no       smallint NOT NULL UNIQUE,
    label_ro text NOT NULL
);
INSERT INTO ref.exam_option VALUES
    ('written', 'form', 1, 'Scris'),
    ('test', 'form', 2, 'Grilă'),
    ('oral', 'form', 3, 'Oral'),
    ('practical', 'form', 4, 'Practic / la calculator'),
    ('project', 'form', 5, 'Susținere proiect'),
    ('theory', 'content', 6, 'Teorie'),
    ('problems', 'content', 7, 'Probleme / exerciții');
GRANT SELECT ON ref.exam_option TO aciee_ident, aciee_anon, aciee_report;

ALTER TABLE anon.submission ADD COLUMN exam text[]
    CHECK (cardinality(exam) > 0
       AND exam <@ ARRAY['written', 'test', 'oral', 'practical', 'project', 'theory', 'problems']);
UPDATE anon.submission
SET exam = CASE exam_format WHEN 'theory' THEN '{theory}' WHEN 'practical' THEN '{practical}' WHEN 'oral' THEN '{oral}' END::text[]
WHERE exam_format IN ('theory', 'practical', 'oral');

DROP VIEW report.course_survival;
DROP MATERIALIZED VIEW anon.course_survival;
ALTER TABLE anon.submission DROP COLUMN exam_format;
DROP TABLE ref.exam_format;

CREATE MATERIALIZED VIEW anon.course_survival AS
WITH sub AS (
    SELECT sr.root_id AS subject_id, s.*
    FROM anon.submission s
    JOIN core.course_offering o ON o.id = s.offering_id
    JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
    JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id),
pick AS (
    SELECT sub.subject_id, sub.key_hash, x.kind, x.no, x.label_ro
    FROM sub
    CROSS JOIN LATERAL unnest(sub.exam) picked(code)
    JOIN ref.exam_option x ON x.code = picked.code),
share AS (
    SELECT p.subject_id, p.kind,
           string_agg(p.label_ro || ' ' || round(100.0 * p.picked / a.answered) || '%', ' · ' ORDER BY p.picked DESC, p.no) AS text
    FROM (SELECT subject_id, kind, no, label_ro, count(*) AS picked FROM pick GROUP BY subject_id, kind, no, label_ro) p
    JOIN (SELECT subject_id, kind, count(DISTINCT key_hash) AS answered FROM pick GROUP BY subject_id, kind) a USING (subject_id, kind)
    CROSS JOIN ref.setting s
    WHERE a.answered >= s.k_threshold
    GROUP BY p.subject_id, p.kind)
SELECT sub.subject_id, count(*) AS n,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.exam_difficulty), 1) END AS exam_difficulty,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.labs_help), 1) END AS labs_help,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.attendance_strictness), 1) END AS attendance_strictness,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.weekly_hours), 1) END AS weekly_hours,
       CASE WHEN count(*) >= s.k_threshold THEN mode() WITHIN GROUP (ORDER BY sub.attendance_useful) END AS attendance_useful,
       form.text AS exam_form,
       content.text AS exam_content
FROM sub
CROSS JOIN ref.setting s
LEFT JOIN share form ON form.subject_id = sub.subject_id AND form.kind = 'form'
LEFT JOIN share content ON content.subject_id = sub.subject_id AND content.kind = 'content'
GROUP BY sub.subject_id, s.k_threshold, form.text, content.text;
CREATE UNIQUE INDEX course_survival_key ON anon.course_survival (subject_id);
REVOKE INSERT, UPDATE, DELETE ON anon.course_survival FROM aciee_anon;
GRANT SELECT ON anon.course_survival TO aciee_anon, aciee_ident;

CREATE VIEW report.course_survival AS
SELECT s.name_ro AS subject, ss.specialization_ids, cs.n,
       cs.exam_difficulty, cs.labs_help, cs.attendance_strictness, cs.weekly_hours,
       au.label_ro AS attendance_useful, cs.exam_form, cs.exam_content
FROM anon.course_survival cs
JOIN core.subject s ON s.id = cs.subject_id
LEFT JOIN report.subject_specialization ss ON ss.subject_id = cs.subject_id
LEFT JOIN ref.attendance_usefulness au ON au.code = cs.attendance_useful;
GRANT SELECT ON report.course_survival TO aciee_report;
--rollback SELECT 1;
