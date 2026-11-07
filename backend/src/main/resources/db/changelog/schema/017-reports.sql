--liquibase formatted sql

--changeset aciee:017-report-views
CREATE SCHEMA report;

CREATE VIEW report.subject_specialization AS
SELECT sr.root_id AS subject_id, array_agg(DISTINCT pl.specialization_id) AS specialization_ids
FROM core.subject_root_v sr
JOIN core.curriculum_item ci ON ci.subject_id = sr.subject_id
JOIN core.study_plan pl ON pl.id = ci.plan_id
GROUP BY sr.root_id;

CREATE VIEW report.prof_subject AS
SELECT p.full_name AS professor, p.department_id, d.name_ro AS department, s.name_ro AS subject,
       ss.specialization_ids, r.n,
       r.a01, r.a02, r.a03, r.a04, r.a05, r.a06, r.a07, r.a08, r.a09, r.a10, r.a11, r.a12, r.a13, r.a14
FROM anon.prof_subject_radar r
JOIN core.professor p ON p.id = r.professor_id
JOIN core.department d ON d.id = p.department_id
JOIN core.subject s ON s.id = r.subject_id
LEFT JOIN report.subject_specialization ss ON ss.subject_id = r.subject_id;

CREATE VIEW report.course_survival AS
SELECT s.name_ro AS subject, ss.specialization_ids, cs.n,
       cs.exam_difficulty, cs.labs_help, cs.attendance_strictness, cs.weekly_hours,
       au.label_ro AS attendance_useful, ef.label_ro AS exam_format
FROM anon.course_survival cs
JOIN core.subject s ON s.id = cs.subject_id
LEFT JOIN report.subject_specialization ss ON ss.subject_id = cs.subject_id
LEFT JOIN ref.attendance_usefulness au ON au.code = cs.attendance_useful
LEFT JOIN ref.exam_format ef ON ef.code = cs.exam_format;

GRANT USAGE ON SCHEMA report TO aciee_report;
GRANT SELECT ON report.prof_subject, report.course_survival TO aciee_report;
REVOKE SELECT ON anon.prof_subject_radar, anon.course_survival FROM aciee_report;
REVOKE USAGE ON SCHEMA anon FROM aciee_report;
--rollback DROP SCHEMA report CASCADE;
--rollback GRANT USAGE ON SCHEMA anon TO aciee_report;
--rollback GRANT SELECT ON anon.prof_subject_radar, anon.course_survival TO aciee_report;
