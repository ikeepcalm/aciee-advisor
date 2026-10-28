--liquibase formatted sql

--changeset aciee:003-ref
CREATE TABLE ref.component (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.component VALUES
    ('lecture', 'Curs'),
    ('lab', 'Laborator'),
    ('seminar', 'Seminar'),
    ('project', 'Proiect');

CREATE TABLE ref.academic_title (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.academic_title VALUES
    ('prof', 'Prof. univ. dr.'),
    ('conf', 'Conf. univ. dr.'),
    ('sl', 'Ș.l. dr.'),
    ('lect', 'Lect. univ. dr.'),
    ('asist', 'Asist. univ.'),
    ('asoc', 'Cadru didactic asociat');

CREATE TABLE ref.exam_format (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.exam_format VALUES
    ('theory', 'Teorie'),
    ('practical', 'Practic'),
    ('oral', 'Oral'),
    ('mixed', 'Mixt');

CREATE TABLE ref.attendance_usefulness (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.attendance_usefulness VALUES
    ('yes', 'Da'),
    ('partly', 'Parțial'),
    ('no', 'Nu');

CREATE TABLE ref.moderation_reason (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.moderation_reason VALUES
    ('insult', 'Insultă'),
    ('accusation', 'Acuzație'),
    ('personal_data', 'Date personale'),
    ('off_topic', 'În afara subiectului');

CREATE TABLE ref.report_reason (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.report_reason VALUES
    ('insult', 'Insultă'),
    ('accusation', 'Acuzație'),
    ('personal_data', 'Date personale'),
    ('off_topic', 'În afara subiectului'),
    ('false', 'Informație falsă');

CREATE TABLE ref.pillar (
    code     text PRIMARY KEY,
    no       smallint NOT NULL UNIQUE,
    label_ro text NOT NULL
);
INSERT INTO ref.pillar VALUES
    ('teaching', 1, 'Predare, organizare și tehnologie'),
    ('practice', 2, 'Laboratoare și lucrări practice'),
    ('grading', 3, 'Notare și comunicare');

CREATE TABLE ref.axis (
    code        text PRIMARY KEY CHECK (code ~ '^a[0-9]{2}$'),
    pillar_code text NOT NULL REFERENCES ref.pillar,
    label_ro    text NOT NULL,
    label_en    text NOT NULL
);
INSERT INTO ref.axis VALUES
    ('a01', 'teaching', 'Stăpânirea platformelor digitale / online', 'Mastery of digital / remote learning platforms'),
    ('a02', 'teaching', 'Actualitatea tehnologiilor predate', 'Modernity and industry relevance of technologies taught'),
    ('a03', 'teaching', 'Calitatea suportului de curs și a materialelor asincrone', 'Sufficiency and quality of asynchronous materials'),
    ('a04', 'teaching', 'Organizarea și structura orelor', 'Class organisation and structure'),
    ('a05', 'practice', 'Corelarea laboratoarelor cu teoria', 'Alignment of laboratories with theory'),
    ('a06', 'practice', 'Claritatea și confortul predării laboratoarelor', 'Clarity and convenience of lab check-ins'),
    ('a07', 'practice', 'Relevanța lucrărilor practice / proiectelor', 'Relevance of practical/project work'),
    ('a08', 'practice', 'Confortul predării lucrărilor practice / proiectelor', 'Convenience of practical/project defences'),
    ('a09', 'grading', 'Transparența sistemului de notare', 'Transparency of the grading system'),
    ('a10', 'grading', 'Corectitudinea și relevanța sistemului de notare', 'Fairness of the grading system'),
    ('a11', 'grading', 'Stabilitatea regulilor de notare pe parcursul semestrului', 'Stability of grading rules during the semester'),
    ('a12', 'grading', 'Accesibilitatea și vizibilitatea punctajelor pe parcurs', 'Accessibility and visibility of grades'),
    ('a13', 'grading', 'Deschiderea la comunicare și disponibilitatea', 'Openness to communication and availability'),
    ('a14', 'grading', 'Atitudinea respectuoasă față de studenți', 'Respectful treatment of students');

CREATE TABLE ref.app_role (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.app_role VALUES
    ('moderator', 'Moderator'),
    ('admin', 'Administrator');

CREATE TABLE ref.membership_kind (
    code     text PRIMARY KEY,
    label_ro text NOT NULL
);
INSERT INTO ref.membership_kind VALUES
    ('regular', 'Membru'),
    ('repeat', 'Repetent'),
    ('historical_claim', 'An anterior');

CREATE TABLE ref.setting (
    id          smallint PRIMARY KEY CHECK (id = 1),
    k_threshold smallint NOT NULL CHECK (k_threshold >= 2)
);
INSERT INTO ref.setting VALUES (1, 5);
--rollback DROP TABLE ref.setting, ref.membership_kind, ref.app_role, ref.axis, ref.pillar, ref.report_reason,
--rollback     ref.moderation_reason, ref.attendance_usefulness, ref.exam_format, ref.academic_title, ref.component;
