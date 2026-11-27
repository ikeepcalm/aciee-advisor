--liquibase formatted sql

--changeset aciee:027-component-criteria
DROP VIEW report.prof_subject;
DROP VIEW anon.published_post;
DROP MATERIALIZED VIEW anon.prof_subject_radar;
DROP VIEW anon.rating_v;

ALTER TABLE anon.rating
    ADD COLUMN a15 ref.score, ADD COLUMN a16 ref.score, ADD COLUMN a17 ref.score, ADD COLUMN a18 ref.score, ADD COLUMN a19 ref.score,
    ADD COLUMN a20 ref.score, ADD COLUMN a21 ref.score, ADD COLUMN a22 ref.score, ADD COLUMN a23 ref.score, ADD COLUMN a24 ref.score,
    DROP CONSTRAINT rating_check;

-- old criteria carry over only where the new one asks the same thing; the merged pairs keep the rounded mean of what was answered
UPDATE anon.rating r
SET a01 = (SELECT round(avg(v)) FROM (VALUES (r.a09), (r.a10)) x(v)),
    a02 = r.a11,
    a03 = r.a12,
    a04 = (SELECT round(avg(v)) FROM (VALUES (r.a13), (r.a14)) x(v)),
    a05 = NULL,
    a06 = CASE ta.component WHEN 'lecture' THEN r.a04 END,
    a07 = CASE ta.component WHEN 'lecture' THEN r.a03 END,
    a08 = CASE ta.component WHEN 'lecture' THEN r.a02 END,
    a09 = NULL,
    a10 = NULL,
    a11 = NULL,
    a12 = CASE ta.component WHEN 'lab' THEN r.a05 END,
    a13 = NULL,
    a14 = NULL,
    a21 = CASE ta.component WHEN 'project' THEN r.a07 END
FROM core.teaching_assignment ta
WHERE ta.id = r.teaching_assignment_id;
DELETE FROM anon.rating WHERE num_nonnulls(a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21, a22, a23, a24) = 0;
ALTER TABLE anon.rating ADD CONSTRAINT rating_check CHECK (num_nonnulls(a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21, a22, a23, a24) >= 1);

DELETE FROM ref.axis;
DELETE FROM ref.pillar WHERE code IN ('teaching', 'practice');
UPDATE ref.pillar SET no = 5 WHERE code = 'grading';
INSERT INTO ref.pillar VALUES
    ('lecture', 1, 'Curs'),
    ('lab', 2, 'Laborator'),
    ('seminar', 3, 'Seminar'),
    ('project', 4, 'Proiect');
INSERT INTO ref.axis VALUES
    ('a01', 'grading', 'Notare corectă și clară', 'Fair and clear grading'),
    ('a02', 'grading', 'Reguli stabile tot semestrul', 'Rules that stay the same all semester'),
    ('a03', 'grading', 'Note vizibile pe parcurs', 'Grades visible during the semester'),
    ('a04', 'grading', 'Respectuos și ușor de contactat', 'Respectful and easy to reach'),
    ('a05', 'lecture', 'Explicații clare', 'Clear explanations'),
    ('a06', 'lecture', 'Structură și ritm', 'Structure and pace'),
    ('a07', 'lecture', 'Calitatea suportului de curs', 'Quality of course materials'),
    ('a08', 'lecture', 'Conținut actual', 'Up-to-date content'),
    ('a09', 'lecture', 'Răspunde la întrebări în curs', 'Answers questions in class'),
    ('a10', 'lab', 'Îndrumare de laborator clare', 'Clear lab instructions'),
    ('a11', 'lab', 'Ajutor în timpul laboratorului', 'Help during the lab'),
    ('a12', 'lab', 'Legătura cu ce se predă la curs', 'Linked to what the course teaches'),
    ('a13', 'lab', 'Verificare corectă a lucrărilor', 'Fair checking of lab work'),
    ('a14', 'lab', 'Timp suficient pentru lucrarea dată', 'Enough time for the work set'),
    ('a15', 'lab', 'Reguli clare de prezență și recuperare', 'Clear attendance and make-up rules'),
    ('a16', 'seminar', 'Probleme utile pentru examen', 'Problems useful for the exam'),
    ('a17', 'seminar', 'Soluții bine explicate', 'Solutions explained well'),
    ('a18', 'seminar', 'Încurajează participarea, greșelile sunt în regulă', 'Encourages taking part; mistakes are fine'),
    ('a19', 'seminar', 'Notare corectă a activității de seminar', 'Fair grading of seminar activity'),
    ('a20', 'project', 'Cerințe și termene clare', 'Clear requirements and deadlines'),
    ('a21', 'project', 'Temă utilă și relevantă', 'Useful, relevant topic'),
    ('a22', 'project', 'Îndrumare și feedback pe parcurs', 'Guidance and feedback along the way'),
    ('a23', 'project', 'Susținere și evaluare corectă', 'Fair final presentation and evaluation'),
    ('a24', 'project', 'Volum de muncă realist', 'Realistic amount of work');

CREATE VIEW anon.rating_v AS
SELECT r.key_hash, ta.professor_id, ta.component, sr.root_id AS subject_id,
       r.a01, r.a02, r.a03, r.a04, r.a05, r.a06, r.a07, r.a08, r.a09, r.a10, r.a11, r.a12,
       r.a13, r.a14, r.a15, r.a16, r.a17, r.a18, r.a19, r.a20, r.a21, r.a22, r.a23, r.a24
FROM anon.rating r
JOIN core.teaching_assignment ta ON ta.id = r.teaching_assignment_id
JOIN core.course_offering o ON o.id = r.offering_id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id;

-- one row per class type; each criterion needs k answers of its own, since quick mode leaves most of them empty
CREATE MATERIALIZED VIEW anon.prof_subject_radar AS
SELECT r.professor_id, r.subject_id, r.component, count(DISTINCT r.key_hash) AS n,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a01 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a01), 1) END AS a01,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a02 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a02), 1) END AS a02,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a03 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a03), 1) END AS a03,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a04 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a04), 1) END AS a04,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a05 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a05), 1) END AS a05,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a06 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a06), 1) END AS a06,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a07 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a07), 1) END AS a07,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a08 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a08), 1) END AS a08,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a09 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a09), 1) END AS a09,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a10 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a10), 1) END AS a10,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a11 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a11), 1) END AS a11,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a12 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a12), 1) END AS a12,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a13 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a13), 1) END AS a13,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a14 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a14), 1) END AS a14,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a15 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a15), 1) END AS a15,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a16 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a16), 1) END AS a16,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a17 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a17), 1) END AS a17,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a18 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a18), 1) END AS a18,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a19 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a19), 1) END AS a19,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a20 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a20), 1) END AS a20,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a21 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a21), 1) END AS a21,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a22 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a22), 1) END AS a22,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a23 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a23), 1) END AS a23,
       CASE WHEN count(DISTINCT r.key_hash) FILTER (WHERE r.a24 IS NOT NULL) >= s.k_threshold THEN round(avg(r.a24), 1) END AS a24
FROM anon.rating_v r
CROSS JOIN ref.setting s
GROUP BY r.professor_id, r.subject_id, r.component, s.k_threshold;
CREATE UNIQUE INDEX prof_subject_radar_key ON anon.prof_subject_radar (professor_id, subject_id, component);
REVOKE INSERT, UPDATE, DELETE ON anon.prof_subject_radar FROM aciee_anon;
GRANT SELECT ON anon.prof_subject_radar TO aciee_anon, aciee_ident;

CREATE VIEW anon.published_post AS
SELECT p.id, p.offering_id, p.kind, p.teaching_assignment_id, v.version_no, v.body, v.moderated_week
FROM anon.post p
CROSS JOIN LATERAL (
    SELECT pv.version_no, pv.body, pv.moderated_week
    FROM anon.post_version pv
    WHERE pv.post_id = p.id AND pv.status = 'approved'
    ORDER BY pv.version_no DESC
    LIMIT 1) v
WHERE p.kind = 'tip' OR EXISTS (
        SELECT 1
        FROM core.teaching_assignment ta
        JOIN core.course_offering o ON o.id = ta.offering_id
        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
        JOIN anon.prof_subject_radar r ON r.professor_id = ta.professor_id AND r.subject_id = sr.root_id AND r.component = ta.component
        CROSS JOIN ref.setting s
        WHERE ta.id = p.teaching_assignment_id AND r.n >= s.k_threshold
        -- OFFSET 0 keeps this a per-row subplan; without it the planner hashes the k check over every assignment
        OFFSET 0);
GRANT SELECT ON anon.published_post TO aciee_anon, aciee_ident;

CREATE VIEW report.prof_subject AS
SELECT p.full_name AS professor, p.department_id, d.name_ro AS department, s.name_ro AS subject, c.label_ro AS component,
       ss.specialization_ids, r.n,
       r.a01, r.a02, r.a03, r.a04, r.a05, r.a06, r.a07, r.a08, r.a09, r.a10, r.a11, r.a12,
       r.a13, r.a14, r.a15, r.a16, r.a17, r.a18, r.a19, r.a20, r.a21, r.a22, r.a23, r.a24
FROM anon.prof_subject_radar r
JOIN core.professor p ON p.id = r.professor_id
JOIN core.department d ON d.id = p.department_id
JOIN core.subject s ON s.id = r.subject_id
JOIN ref.component c ON c.code = r.component
LEFT JOIN report.subject_specialization ss ON ss.subject_id = r.subject_id;
GRANT SELECT ON report.prof_subject TO aciee_report;
--rollback SELECT 1;
