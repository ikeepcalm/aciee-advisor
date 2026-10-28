--liquibase formatted sql

--changeset aciee:010-anon
CREATE TABLE anon.offering_key (
    fingerprint ref.sha256 PRIMARY KEY,
    offering_id integer NOT NULL REFERENCES core.course_offering,
    public_key  bytea NOT NULL,
    active      boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX offering_key_one_active_idx ON anon.offering_key (offering_id) WHERE active;

CREATE TABLE anon.submission (
    key_hash              ref.sha256 PRIMARY KEY,
    offering_id           integer NOT NULL REFERENCES core.course_offering,
    key_fingerprint       ref.sha256 NOT NULL REFERENCES anon.offering_key,
    exam_difficulty       ref.score,
    labs_help             ref.score,
    attendance_useful     text REFERENCES ref.attendance_usefulness,
    weekly_hours          smallint CHECK (weekly_hours BETWEEN 0 AND 80),
    exam_format           text REFERENCES ref.exam_format,
    attendance_strictness ref.score,
    UNIQUE (key_hash, offering_id)
);

CREATE TABLE anon.rating (
    key_hash               ref.sha256 NOT NULL,
    teaching_assignment_id integer NOT NULL,
    offering_id            integer NOT NULL,
    a01 ref.score, a02 ref.score, a03 ref.score, a04 ref.score, a05 ref.score, a06 ref.score, a07 ref.score,
    a08 ref.score, a09 ref.score, a10 ref.score, a11 ref.score, a12 ref.score, a13 ref.score, a14 ref.score,
    PRIMARY KEY (key_hash, teaching_assignment_id),
    FOREIGN KEY (key_hash, offering_id) REFERENCES anon.submission (key_hash, offering_id) ON DELETE CASCADE,
    FOREIGN KEY (offering_id, teaching_assignment_id) REFERENCES core.teaching_assignment (offering_id, id),
    CHECK (num_nonnulls(a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14) >= 1)
) WITH (autovacuum_vacuum_insert_scale_factor = 0.05);
CREATE STATISTICS anon.rating_assignment_offering (dependencies)
    ON teaching_assignment_id, offering_id FROM anon.rating;

CREATE TABLE anon.post (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash               ref.sha256 NOT NULL,
    offering_id            integer NOT NULL,
    kind                   text NOT NULL CHECK (kind IN ('tip', 'comment')),
    teaching_assignment_id integer,
    CHECK ((kind = 'comment') = (teaching_assignment_id IS NOT NULL)),
    UNIQUE (id, kind),
    UNIQUE (key_hash, teaching_assignment_id),
    FOREIGN KEY (key_hash, offering_id) REFERENCES anon.submission (key_hash, offering_id) ON DELETE CASCADE,
    FOREIGN KEY (offering_id, teaching_assignment_id) REFERENCES core.teaching_assignment (offering_id, id)
);
CREATE UNIQUE INDEX post_one_tip_idx ON anon.post (key_hash) WHERE kind = 'tip';
CREATE INDEX post_tip_offering_idx ON anon.post (offering_id) WHERE kind = 'tip';
CREATE INDEX post_comment_assignment_idx ON anon.post (teaching_assignment_id) WHERE kind = 'comment';

CREATE TABLE anon.post_version (
    post_id        uuid NOT NULL,
    version_no     smallint NOT NULL CHECK (version_no >= 1),
    kind           text NOT NULL,
    body           text NOT NULL CHECK (length(btrim(body)) >= 1),
    status         text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reason_code    text REFERENCES ref.moderation_reason,
    submitted_week ref.week_start NOT NULL,
    moderated_week ref.week_start,
    PRIMARY KEY (post_id, version_no),
    FOREIGN KEY (post_id, kind) REFERENCES anon.post (id, kind) ON DELETE CASCADE,
    CHECK (length(body) <= CASE kind WHEN 'tip' THEN 1000 ELSE 280 END),
    CHECK ((status = 'rejected') = (reason_code IS NOT NULL)),
    CHECK ((status = 'pending') = (moderated_week IS NULL))
);
CREATE INDEX post_version_queue_idx ON anon.post_version (submitted_week, post_id, version_no) WHERE status = 'pending';

CREATE TABLE anon.post_report (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id       uuid NOT NULL REFERENCES anon.post ON DELETE CASCADE,
    reason_code   text NOT NULL REFERENCES ref.report_reason,
    reported_week ref.week_start NOT NULL,
    status        text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'dismissed', 'upheld'))
);
CREATE INDEX post_report_open_idx ON anon.post_report (post_id) WHERE status = 'open';
--rollback DROP TABLE anon.post_report, anon.post_version, anon.post, anon.rating, anon.submission, anon.offering_key;

--changeset aciee:010-aggregates
CREATE VIEW anon.rating_v AS
SELECT r.key_hash, ta.professor_id, sr.root_id AS subject_id,
       r.a01, r.a02, r.a03, r.a04, r.a05, r.a06, r.a07, r.a08, r.a09, r.a10, r.a11, r.a12, r.a13, r.a14
FROM anon.rating r
JOIN core.teaching_assignment ta ON ta.id = r.teaching_assignment_id
JOIN core.course_offering o ON o.id = r.offering_id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id;

CREATE MATERIALIZED VIEW anon.prof_subject_radar AS
SELECT r.professor_id, r.subject_id, count(DISTINCT r.key_hash) AS n,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a01), 1) END AS a01,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a02), 1) END AS a02,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a03), 1) END AS a03,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a04), 1) END AS a04,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a05), 1) END AS a05,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a06), 1) END AS a06,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a07), 1) END AS a07,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a08), 1) END AS a08,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a09), 1) END AS a09,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a10), 1) END AS a10,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a11), 1) END AS a11,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a12), 1) END AS a12,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a13), 1) END AS a13,
       CASE WHEN count(DISTINCT r.key_hash) >= s.k_threshold THEN round(avg(r.a14), 1) END AS a14
FROM anon.rating_v r
CROSS JOIN ref.setting s
GROUP BY r.professor_id, r.subject_id, s.k_threshold;
CREATE UNIQUE INDEX prof_subject_radar_key ON anon.prof_subject_radar (professor_id, subject_id);

CREATE MATERIALIZED VIEW anon.course_survival AS
SELECT sr.root_id AS subject_id, count(*) AS n,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.exam_difficulty), 1) END AS exam_difficulty,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.labs_help), 1) END AS labs_help,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.attendance_strictness), 1) END AS attendance_strictness,
       CASE WHEN count(*) >= s.k_threshold THEN round(avg(sub.weekly_hours), 1) END AS weekly_hours,
       CASE WHEN count(*) >= s.k_threshold THEN mode() WITHIN GROUP (ORDER BY sub.attendance_useful) END AS attendance_useful,
       CASE WHEN count(*) >= s.k_threshold THEN mode() WITHIN GROUP (ORDER BY sub.exam_format) END AS exam_format
FROM anon.submission sub
JOIN core.course_offering o ON o.id = sub.offering_id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
CROSS JOIN ref.setting s
GROUP BY sr.root_id, s.k_threshold;
CREATE UNIQUE INDEX course_survival_key ON anon.course_survival (subject_id);
--rollback DROP MATERIALIZED VIEW anon.course_survival; DROP MATERIALIZED VIEW anon.prof_subject_radar; DROP VIEW anon.rating_v;

--changeset aciee:010-refresh splitStatements:false
CREATE FUNCTION anon.refresh_aggregates() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog
SET statement_timeout = '10min' AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY anon.prof_subject_radar;
    REFRESH MATERIALIZED VIEW CONCURRENTLY anon.course_survival;
END
$$;
--rollback DROP FUNCTION anon.refresh_aggregates();

--changeset aciee:010-post-views
CREATE VIEW anon.published_post AS
SELECT DISTINCT ON (p.id)
       p.id, p.offering_id, p.kind, p.teaching_assignment_id, v.version_no, v.body, v.moderated_week
FROM anon.post p
JOIN anon.post_version v ON v.post_id = p.id
WHERE v.status = 'approved'
  AND v.moderated_week < date_trunc('week', current_date)
  AND (p.kind = 'tip' OR EXISTS (
        SELECT 1
        FROM core.teaching_assignment ta
        JOIN core.course_offering o ON o.id = ta.offering_id
        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
        JOIN anon.prof_subject_radar r ON r.professor_id = ta.professor_id AND r.subject_id = sr.root_id
        CROSS JOIN ref.setting s
        WHERE ta.id = p.teaching_assignment_id AND r.n >= s.k_threshold))
ORDER BY p.id, v.version_no DESC;

CREATE VIEW anon.moderation_queue AS
SELECT v.post_id, v.version_no, v.kind, v.body, v.submitted_week, p.offering_id
FROM anon.post_version v
JOIN anon.post p ON p.id = v.post_id
WHERE v.status = 'pending';
--rollback DROP VIEW anon.moderation_queue; DROP VIEW anon.published_post;
