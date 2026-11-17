--liquibase formatted sql

--changeset aciee:demo-data context:demo
CREATE FUNCTION pg_temp.h(seed text) RETURNS float8
LANGUAGE sql IMMUTABLE
RETURN (('x' || substr(md5(seed), 1, 7))::bit(28)::int)::float8 / 268435456.0;

CREATE FUNCTION pg_temp.nz(seed text) RETURNS float8
LANGUAGE sql IMMUTABLE
RETURN (pg_temp.h(seed || '/1') + pg_temp.h(seed || '/2') + pg_temp.h(seed || '/3') - 1.5) * 2;

CREATE FUNCTION pg_temp.clamp10(x float8) RETURNS smallint
LANGUAGE sql IMMUTABLE
RETURN least(10, greatest(1, round(x)))::smallint;

CREATE FUNCTION pg_temp.sc(base float8, strong text[], weak text[], slug text, axis text, k text, ta integer, comp text, subj text)
RETURNS smallint
LANGUAGE sql IMMUTABLE
RETURN CASE
    WHEN pg_temp.h(k || '/' || ta || '/' || axis || '/null') <
         CASE WHEN comp = 'lecture' AND axis IN ('a05', 'a06', 'a07', 'a08') THEN 0.85
              WHEN comp <> 'lecture' AND axis IN ('a01', 'a02', 'a03') THEN 0.35
              ELSE 0.06 END THEN NULL
    ELSE pg_temp.clamp10(base
                         + 0.4 * pg_temp.nz(slug || '/' || subj)
                         + CASE WHEN axis = ANY (strong) THEN 1.1
                                WHEN axis = ANY (weak) THEN -1.5
                                ELSE 0.35 * pg_temp.nz(slug || '/' || axis) END
                         + 0.7 * pg_temp.nz(k || '/mood')
                         + pg_temp.nz(k || '/' || ta || '/' || axis))
    END;

INSERT INTO core.academic_year (during)
SELECT daterange(make_date(y, 9, 1), make_date(y + 1, 9, 1)) FROM generate_series(2023, 2026) y;

CREATE TEMP TABLE dm_year ON COMMIT DROP AS
SELECT y, a.id, a.during
FROM generate_series(2023, 2026) y
JOIN core.academic_year a ON lower(a.during) = make_date(y, 9, 1);

INSERT INTO core.semester (academic_year_id, no, during, breaks, rating_opens_at)
SELECT y.id, n.no,
       CASE WHEN y.y = 2026 AND n.no = 1 THEN daterange('2026-09-28', '2027-02-15')
            WHEN y.y = 2026 THEN daterange('2027-02-22', '2027-07-31')
            WHEN n.no = 1 THEN daterange(make_date(y.y, 10, 2), make_date(y.y + 1, 2, 13))
            ELSE daterange(make_date(y.y + 1, 2, 23), make_date(y.y + 1, 6, 30)) END,
       CASE WHEN y.y = 2026 AND n.no = 1 THEN datemultirange(daterange('2026-12-19', '2027-01-11'))
            WHEN y.y = 2026 THEN datemultirange(daterange('2027-04-30', '2027-05-10'))
            WHEN n.no = 1 THEN datemultirange(daterange(make_date(y.y, 12, 20), make_date(y.y + 1, 1, 8)))
            ELSE datemultirange(daterange(make_date(y.y + 1, 4, 25), make_date(y.y + 1, 5, 5))) END,
       CASE WHEN y.y = 2026 AND n.no = 1 THEN timestamptz '2027-01-23'
            WHEN y.y = 2026 THEN timestamptz '2027-06-05'
            WHEN n.no = 1 THEN make_date(y.y + 1, 1, 24)::timestamptz
            ELSE make_date(y.y + 1, 6, 5)::timestamptz END
FROM dm_year y CROSS JOIN (VALUES (1), (2)) n(no);

INSERT INTO core.department (faculty_id, name_ro)
SELECT f.id, d.name_ro
FROM core.faculty f
CROSS JOIN (VALUES ('Calculatoare și Tehnologia Informației'), ('Automatică și Electrotehnică'),
                   ('Electronică și Telecomunicații')) d(name_ro)
WHERE f.code = 'ACIEE'
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE dm_cand ON COMMIT DROP AS
SELECT y.y, y.id AS year_id, y.during, sp.spec, sy.study_year, y.y - sy.study_year + 1 AS intake, l.letter,
       right((y.y - sy.study_year + 1)::text, 2) || CASE sp.spec WHEN 'C' THEN 'C' ELSE 'A' END || sy.study_year || '1' || l.letter AS code
FROM dm_year y
CROSS JOIN (VALUES ('C', 1, 'AB'), ('C', 2, 'AB'), ('C', 3, 'AB'), ('C', 4, 'A'),
                   ('AIA', 1, 'A'), ('AIA', 2, 'A'), ('AIA', 3, 'A'), ('AIA', 4, 'A')) sp(spec, study_year, letters)
CROSS JOIN LATERAL (SELECT sp.study_year) sy
CROSS JOIN LATERAL regexp_split_to_table(sp.letters, '') l(letter)
WHERE y.y - sy.study_year + 1 >= 2023;

INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
SELECT p.id, c.year_id, c.study_year, c.code, 30
FROM dm_cand c
JOIN core.specialization s ON s.code = c.spec
JOIN core.study_plan p ON p.specialization_id = s.id AND p.intake @> c.intake;

CREATE TEMP TABLE dm_group ON COMMIT DROP AS
SELECT g.id, g.plan_id, g.code, g.study_year, c.y, c.spec, g.academic_year_id, c.during
FROM dm_cand c
JOIN core.study_group g ON g.academic_year_id = c.year_id AND g.code = c.code;

INSERT INTO core.subgroup (group_id, label) SELECT g.id, l.label FROM dm_group g CROSS JOIN (VALUES ('1'), ('2')) l(label);

INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
SELECT ci.id, g.id, g.plan_id, g.study_year, s.name_ro
FROM dm_group g
JOIN core.curriculum_item ci ON ci.plan_id = g.plan_id AND ci.study_year = g.study_year
JOIN core.subject s ON s.id = ci.subject_id;

CREATE TEMP TABLE dm_prof (slug text, full_name text, title_code text, dept text, base float8, strong text[], weak text[]) ON COMMIT DROP;
INSERT INTO dm_prof VALUES
    ('mircea-dragomir', 'Mircea Dragomir', 'prof', 'Calculatoare și Tehnologia Informației', 9.0, '{a04,a09,a14}', '{a01}'),
    ('corina-balanescu', 'Corina Bălănescu', 'conf', 'Calculatoare și Tehnologia Informației', 8.8, '{a03,a12,a13}', '{a02}'),
    ('raluca-serban', 'Raluca Șerban', 'lect', 'Calculatoare și Tehnologia Informației', 9.2, '{a09,a13,a14}', '{a01}'),
    ('alexandru-ciurea', 'Alexandru Ciurea', 'asist', 'Calculatoare și Tehnologia Informației', 8.4, '{a06,a08,a13,a14}', '{a03}'),
    ('teodora-manole', 'Teodora Manole', 'asist', 'Calculatoare și Tehnologia Informației', 8.0, '{a06,a08,a14}', '{a03}'),
    ('adina-vaduva', 'Adina Văduva', 'sl', 'Calculatoare și Tehnologia Informației', 8.1, '{a02,a07}', '{a11,a12}'),
    ('ioana-cristescu', 'Ioana Cristescu', 'sl', 'Calculatoare și Tehnologia Informației', 7.9, '{a05,a06,a13}', '{a01,a12}'),
    ('sorin-tataru', 'Sorin Tătaru', 'conf', 'Calculatoare și Tehnologia Informației', 7.5, '{a02,a07}', '{a11,a12}'),
    ('nicolae-bratu', 'Nicolae Bratu', 'prof', 'Calculatoare și Tehnologia Informației', 7.4, '{a03,a10,a11}', '{a01,a08}'),
    ('catalin-gheorghiu', 'Cătălin Gheorghiu', 'sl', 'Calculatoare și Tehnologia Informației', 7.1, '{a05,a07}', '{a13,a14}'),
    ('stefan-ciocan', 'Ștefan Ciocan', 'conf', 'Calculatoare și Tehnologia Informației', 6.9, '{a07}', '{a09,a11,a12}'),
    ('laura-dumbrava', 'Laura Dumbravă', 'conf', 'Automatică și Electrotehnică', 7.2, '{a03,a14}', '{a07}'),
    ('gheorghe-ilinca', 'Gheorghe Ilinca', 'prof', 'Automatică și Electrotehnică', 6.6, '{a04}', '{a01,a02,a03}'),
    ('dorina-nistor', 'Dorina Nistor', 'conf', 'Automatică și Electrotehnică', 5.9, '{a14}', '{a09,a10,a11}'),
    ('liviu-enache', 'Liviu Enache', 'conf', 'Electronică și Telecomunicații', 7.0, '{a05,a10}', '{a01,a02}'),
    ('remus-parvu', 'Remus Pârvu', 'sl', 'Electronică și Telecomunicații', 5.6, '{a05}', '{a09,a10,a13,a14}');

INSERT INTO core.professor (full_name, title_code, department_id, slug)
SELECT p.full_name, p.title_code, d.id, p.slug
FROM dm_prof p JOIN core.department d ON d.name_ro = p.dept
ORDER BY p.slug;

CREATE TEMP TABLE dm_map (spec text, subject text, comps text[], prof text, sub text, y_from integer, y_to integer) ON COMMIT DROP;
INSERT INTO dm_map (spec, subject, comps, prof, sub, y_from, y_to)
SELECT m.spec, m.subject, coalesce(m.comps, '{lecture,seminar,lab,project}'), m.prof, m.sub, m.y_from, m.y_to
FROM (VALUES
    ('C', 'Analiză matematică', '{lecture,lab,project}'::text[], 'dorina-nistor', NULL::text, NULL::int, NULL::int),
    ('C', 'Analiză matematică', '{seminar}', 'dorina-nistor', '1', NULL, NULL),
    ('C', 'Analiză matematică', '{seminar}', 'laura-dumbrava', '2', NULL, NULL),
    ('C', 'Algebră liniară, geometrie analitică și diferențială', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('C', 'Programarea calculatoarelor și limbaje de programare I', '{lecture,seminar,project}', 'ioana-cristescu', NULL, NULL, NULL),
    ('C', 'Programarea calculatoarelor și limbaje de programare I', '{lab}', 'alexandru-ciurea', '1', NULL, NULL),
    ('C', 'Programarea calculatoarelor și limbaje de programare I', '{lab}', 'ioana-cristescu', '2', NULL, NULL),
    ('C', 'Programarea calculatoarelor și limbaje de programare II', '{lecture,seminar,project}', 'ioana-cristescu', NULL, NULL, NULL),
    ('C', 'Programarea calculatoarelor și limbaje de programare II', '{lab}', 'alexandru-ciurea', NULL, NULL, NULL),
    ('C', 'Informatică aplicată I', '{lecture,seminar,project}', 'adina-vaduva', NULL, NULL, NULL),
    ('C', 'Informatică aplicată I', '{lab}', 'alexandru-ciurea', NULL, NULL, NULL),
    ('C', 'Informatică aplicată II', '{lecture,seminar,project}', 'adina-vaduva', NULL, NULL, NULL),
    ('C', 'Informatică aplicată II', '{lab}', 'alexandru-ciurea', NULL, NULL, NULL),
    ('C', 'Grafică asistată de calculator', NULL, 'teodora-manole', NULL, NULL, NULL),
    ('C', 'Fizică', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('C', 'Matematici speciale', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('C', 'Teoria probabilităților și statistică matematică', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('C', 'Metode numerice', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('C', 'Electrotehnica', NULL, 'gheorghe-ilinca', NULL, NULL, NULL),
    ('C', 'Matematici discrete', '{lecture,lab,project}', 'laura-dumbrava', NULL, NULL, NULL),
    ('C', 'Matematici discrete', '{seminar}', 'dorina-nistor', NULL, NULL, NULL),
    ('C', 'Limba engleză (anul 1)', NULL, 'raluca-serban', NULL, NULL, NULL),
    ('C', 'Competențe digitale avansate', NULL, 'teodora-manole', NULL, NULL, NULL),
    ('C', 'Sisteme de operare', '{lecture,seminar,project}', 'nicolae-bratu', NULL, NULL, NULL),
    ('C', 'Sisteme de operare', '{lab}', 'alexandru-ciurea', '1', NULL, NULL),
    ('C', 'Sisteme de operare', '{lab}', 'nicolae-bratu', '2', NULL, NULL),
    ('C', 'Dispozitive electronice și electronică analogică', NULL, 'liviu-enache', NULL, NULL, NULL),
    ('C', 'Programarea orientată pe obiecte', '{lecture,seminar,project}', 'ioana-cristescu', NULL, NULL, 2024),
    ('C', 'Programarea orientată pe obiecte', '{lecture,seminar,project}', 'adina-vaduva', NULL, 2025, NULL),
    ('C', 'Programarea orientată pe obiecte', '{lab}', 'ioana-cristescu', '1', NULL, NULL),
    ('C', 'Programarea orientată pe obiecte', '{lab}', 'adina-vaduva', '2', NULL, NULL),
    ('C', 'Electronică digitală', NULL, 'liviu-enache', NULL, NULL, NULL),
    ('C', 'Teoria sistemelor', NULL, 'gheorghe-ilinca', NULL, NULL, NULL),
    ('C', 'Calculatoare numerice I', NULL, 'remus-parvu', NULL, NULL, NULL),
    ('C', 'Prelucrarea imaginilor', NULL, 'adina-vaduva', NULL, NULL, NULL),
    ('C', 'Calculatoare numerice II', NULL, 'remus-parvu', NULL, NULL, NULL),
    ('C', 'Achiziția și prelucrarea datelor', NULL, 'liviu-enache', NULL, NULL, NULL),
    ('C', 'Rețele de calculatoare I', NULL, 'liviu-enache', NULL, NULL, 2024),
    ('C', 'Rețele de calculatoare I', NULL, 'catalin-gheorghiu', NULL, 2025, NULL),
    ('C', 'Structuri de date și algoritmi', '{lecture,seminar,project}', 'mircea-dragomir', NULL, NULL, NULL),
    ('C', 'Structuri de date și algoritmi', '{lab}', 'mircea-dragomir', '1', NULL, NULL),
    ('C', 'Structuri de date și algoritmi', '{lab}', 'alexandru-ciurea', '2', NULL, NULL),
    ('C', 'Baze de date I', '{lecture,seminar,project}', 'corina-balanescu', NULL, NULL, NULL),
    ('C', 'Baze de date I', '{lab}', 'corina-balanescu', '1', NULL, NULL),
    ('C', 'Baze de date I', '{lab}', 'teodora-manole', '2', NULL, NULL),
    ('C', 'Arhitectura sistemelor de calcul', NULL, 'nicolae-bratu', NULL, NULL, NULL),
    ('C', 'Limba engleză (anul 2)', NULL, 'raluca-serban', NULL, NULL, NULL),
    ('C', 'Tehnici de comunicare', NULL, 'raluca-serban', NULL, NULL, NULL),
    ('C', 'Etica și integritate academică', NULL, 'raluca-serban', NULL, NULL, NULL),
    ('C', 'Elemente de grafică pe calculator', NULL, 'teodora-manole', NULL, NULL, NULL),
    ('C', 'Proiectarea cu microprocesoare', NULL, 'liviu-enache', NULL, NULL, NULL),
    ('C', 'Rețele de calculatoare II', NULL, 'catalin-gheorghiu', NULL, NULL, NULL),
    ('C', 'Baze de date II', '{lecture,seminar,project}', 'corina-balanescu', NULL, NULL, NULL),
    ('C', 'Baze de date II', '{lab}', 'corina-balanescu', '1', NULL, NULL),
    ('C', 'Baze de date II', '{lab}', 'teodora-manole', '2', NULL, NULL),
    ('C', 'Limbaje formale și translatoare I', NULL, 'mircea-dragomir', NULL, NULL, NULL),
    ('C', 'Limbaje formale și translatoare II', NULL, 'mircea-dragomir', NULL, NULL, NULL),
    ('C', 'Ingineria programelor', NULL, 'stefan-ciocan', NULL, NULL, NULL),
    ('C', 'Programare Web', NULL, 'sorin-tataru', NULL, NULL, NULL),
    ('C', 'Inteligență artificială', NULL, 'adina-vaduva', NULL, NULL, NULL),
    ('C', 'Programare logică și programare funcțională', '{lecture,seminar,project}', 'adina-vaduva', NULL, NULL, NULL),
    ('C', 'Programare logică și programare funcțională', '{lab}', 'ioana-cristescu', NULL, NULL, NULL),
    ('C', 'Proiectarea aplicațiilor Web', NULL, 'sorin-tataru', NULL, NULL, NULL),
    ('C', 'Proiectarea aplicațiilor orientate pe obiecte', NULL, 'ioana-cristescu', NULL, NULL, NULL),
    ('C', 'Managementul proiectelor software', NULL, 'stefan-ciocan', NULL, NULL, NULL),
    ('C', 'Proiectare interfețe utilizator', NULL, 'teodora-manole', NULL, NULL, NULL),
    ('C', 'Proiectare software', NULL, 'stefan-ciocan', NULL, NULL, NULL),
    ('C', 'Interacțiunea om-calculator', NULL, 'teodora-manole', NULL, NULL, NULL),
    ('C', 'Sisteme de operare avansate', NULL, 'nicolae-bratu', NULL, NULL, NULL),
    ('C', 'Analiza algoritmilor', NULL, 'mircea-dragomir', NULL, NULL, NULL),
    ('C', 'Învățare automată', NULL, 'adina-vaduva', NULL, NULL, NULL),
    ('C', 'Sisteme de programare vizuală', NULL, 'sorin-tataru', NULL, NULL, NULL),
    ('C', 'Comunicare de întreprindere', NULL, 'raluca-serban', NULL, NULL, NULL),
    ('C', 'Sisteme de programe pentru rețele de calculatoare', NULL, 'catalin-gheorghiu', NULL, NULL, NULL),
    ('C', 'Sisteme orientate pe servicii', NULL, 'sorin-tataru', NULL, NULL, NULL),
    ('C', 'Elemente de informatică mobilă', '{lecture,seminar,project}', 'sorin-tataru', NULL, NULL, NULL),
    ('C', 'Elemente de informatică mobilă', '{lab}', 'alexandru-ciurea', NULL, NULL, NULL),
    ('C', 'Antreprenoriat în CTI', NULL, 'stefan-ciocan', NULL, NULL, NULL),
    ('C', 'Afaceri și administrarea firmelor', NULL, 'stefan-ciocan', NULL, NULL, NULL),
    ('C', 'Elaborarea proiectului de diplomă', NULL, 'mircea-dragomir', NULL, NULL, NULL),
    ('C', 'Sisteme concurente și distribuite', NULL, 'nicolae-bratu', NULL, NULL, NULL),
    ('C', 'Sisteme incorporate', NULL, 'liviu-enache', NULL, NULL, NULL),
    ('C', 'Integrarea sistemelor informatice', NULL, 'stefan-ciocan', NULL, NULL, NULL),
    ('C', 'Fiabilitate software', NULL, 'remus-parvu', NULL, NULL, NULL),
    ('C', 'E-commerce', NULL, 'sorin-tataru', NULL, NULL, NULL),
    ('C', 'Sisteme bazate pe cunoștințe', NULL, 'adina-vaduva', NULL, NULL, NULL),
    ('C', 'Sisteme multimedia', NULL, 'teodora-manole', NULL, NULL, NULL),
    ('C', 'Sisteme tolerante la defecte', NULL, 'remus-parvu', NULL, NULL, NULL),
    ('C', 'Evaluarea performanțelor', NULL, 'nicolae-bratu', NULL, NULL, NULL),
    ('C', 'Regăsirea informației', NULL, 'mircea-dragomir', NULL, NULL, NULL),
    ('AIA', 'Analiză matematică', NULL, 'dorina-nistor', NULL, NULL, NULL),
    ('AIA', 'Algebră liniară, geometrie analitică și diferențială', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('AIA', 'Programarea calculatoarelor și limbaje de programare I', '{lecture,seminar,project}', 'ioana-cristescu', NULL, NULL, NULL),
    ('AIA', 'Programarea calculatoarelor și limbaje de programare I', '{lab}', 'alexandru-ciurea', NULL, NULL, NULL),
    ('AIA', 'Informatică aplicată I', NULL, 'adina-vaduva', NULL, NULL, NULL),
    ('AIA', 'Fizică', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('AIA', 'Matematici speciale', NULL, 'laura-dumbrava', NULL, NULL, NULL),
    ('AIA', 'Limba engleză (anul 1)', NULL, 'raluca-serban', NULL, NULL, NULL),
    ('AIA', 'Limba engleză (anul 2)', NULL, 'raluca-serban', NULL, NULL, NULL),
    ('AIA', 'Electrotehnica', NULL, 'gheorghe-ilinca', NULL, NULL, NULL),
    ('AIA', 'Teoria sistemelor', NULL, 'gheorghe-ilinca', NULL, NULL, NULL),
    ('AIA', 'Sisteme inteligente de control', NULL, 'gheorghe-ilinca', NULL, NULL, NULL),
    ('AIA', 'Sisteme de învățare automată', NULL, 'adina-vaduva', NULL, NULL, NULL)
) m(spec, subject, comps, prof, sub, y_from, y_to);

INSERT INTO core.teaching_assignment (offering_id, group_id, professor_id, component, subgroup_id, valid_during)
SELECT o.id, g.id, p.id, c.component, sg.id, g.during
FROM dm_group g
JOIN core.course_offering o ON o.group_id = g.id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject s ON s.id = ci.subject_id
JOIN dm_map m ON m.spec = g.spec AND m.subject = s.name_ro
             AND (m.y_from IS NULL OR g.y >= m.y_from) AND (m.y_to IS NULL OR g.y <= m.y_to)
CROSS JOIN LATERAL unnest(m.comps) c(component)
JOIN LATERAL (VALUES ('lecture', ci.lecture_hours), ('seminar', ci.seminar_hours),
                     ('lab', ci.lab_hours), ('project', ci.project_hours)) hrs(component, hours)
     ON hrs.component = c.component AND hrs.hours > 0
LEFT JOIN core.subgroup sg ON sg.group_id = g.id AND c.component <> 'lecture' AND (m.sub IS NULL OR sg.label = m.sub)
JOIN core.professor p ON p.slug = m.prof
WHERE c.component = 'lecture' OR sg.id IS NOT NULL;

CREATE TEMP TABLE dm_name (kind text, no integer, name text) ON COMMIT DROP;
INSERT INTO dm_name VALUES
    ('f', 0, 'Andrei'), ('f', 1, 'Maria'), ('f', 2, 'Ioana'), ('f', 3, 'Alexandru'), ('f', 4, 'Elena'), ('f', 5, 'Mihai'),
    ('f', 6, 'Ana'), ('f', 7, 'Vlad'), ('f', 8, 'Cristina'), ('f', 9, 'Răzvan'), ('f', 10, 'Denisa'), ('f', 11, 'Bogdan'),
    ('f', 12, 'Andreea'), ('f', 13, 'Cătălin'), ('f', 14, 'Larisa'), ('f', 15, 'Sebastian'), ('f', 16, 'Teodora'),
    ('f', 17, 'Ionuț'), ('f', 18, 'Georgiana'), ('f', 19, 'Marius'), ('f', 20, 'Alina'), ('f', 21, 'Daniel'),
    ('f', 22, 'Bianca'), ('f', 23, 'Cosmin'), ('f', 24, 'Roxana'), ('f', 25, 'Eduard'), ('f', 26, 'Diana'),
    ('f', 27, 'Florin'), ('f', 28, 'Nicoleta'), ('f', 29, 'Tudor'), ('f', 30, 'Mădălina'), ('f', 31, 'Sergiu'),
    ('f', 32, 'Oana'), ('f', 33, 'Gabriel'), ('f', 34, 'Paula'), ('f', 35, 'Costin'), ('f', 36, 'Irina'),
    ('f', 37, 'Lucian'), ('f', 38, 'Patricia'), ('f', 39, 'Adrian'),
    ('l', 0, 'Ilie'), ('l', 1, 'Lupu'), ('l', 2, 'Dumitrescu'), ('l', 3, 'Stan'), ('l', 4, 'Gheorghe'), ('l', 5, 'Rusu'),
    ('l', 6, 'Matei'), ('l', 7, 'Constantin'), ('l', 8, 'Marin'), ('l', 9, 'Tudor'), ('l', 10, 'Dobre'), ('l', 11, 'Florea'),
    ('l', 12, 'Ene'), ('l', 13, 'Lazăr'), ('l', 14, 'Cristea'), ('l', 15, 'Badea'), ('l', 16, 'Mitrea'), ('l', 17, 'Iacob'),
    ('l', 18, 'Păun'), ('l', 19, 'Preda'), ('l', 20, 'Voicu'), ('l', 21, 'Zamfir'), ('l', 22, 'Ursu'), ('l', 23, 'Drăgan'),
    ('l', 24, 'Cazacu'), ('l', 25, 'Nedelcu'), ('l', 26, 'Oprea'), ('l', 27, 'Chirilă'), ('l', 28, 'Bejan'),
    ('l', 29, 'Lăzărescu'), ('l', 30, 'Sima'), ('l', 31, 'Țurcanu'), ('l', 32, 'Roman'), ('l', 33, 'Cojocaru'),
    ('l', 34, 'Grigore'), ('l', 35, 'Enache'), ('l', 36, 'Mihalache');

CREATE TEMP TABLE dm_member ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY g.id, seat) AS n, g.id AS group_id, g.code, g.during, seat,
       NULL::text AS email, NULL::text AS display_name, NULL::integer AS user_id
FROM dm_group g
CROSS JOIN LATERAL generate_series(1, 14 + floor(7 * pg_temp.h('members/' || g.code))::int) seat
WHERE g.y = 2026;

UPDATE dm_member m
SET display_name = f.name || ' ' || l.name,
    email = lower(translate(f.name || '.' || l.name, 'ĂÂÎȘȚăâîșțŞŢşţ', 'AAISTaaistSTst'))
FROM dm_name f, dm_name l
WHERE f.kind = 'f' AND f.no = (m.n * 7) % 40 AND l.kind = 'l' AND l.no = (m.n * 11 + m.n / 40) % 37;

UPDATE dm_member m
SET email = m.email || d.dup
FROM (SELECT n, row_number() OVER (PARTITION BY email ORDER BY n) AS dup FROM dm_member) d
WHERE d.n = m.n AND d.dup > 1;

INSERT INTO core.user_account (email, display_name, password_hash, created_at)
SELECT email || '@student.ugal.ro', display_name,
       replace('#argon2id#v=19#m=16384,t=2,p=1#85g/yriKoiZ2V8IUnMDQjA#znWcYrjUtuAgs9B/dsrYG8rX+bHSi3Vi+WSf3IDh+HE', '#', chr(36)),
       make_date(2026, 9, 25) + (n % 20)::int
FROM dm_member
UNION ALL
SELECT r.email || '@student.ugal.ro', r.display_name,
       replace('#argon2id#v=19#m=16384,t=2,p=1#85g/yriKoiZ2V8IUnMDQjA#znWcYrjUtuAgs9B/dsrYG8rX+bHSi3Vi+WSf3IDh+HE', '#', chr(36)),
       now() - (r.age || ' days')::interval
FROM (VALUES ('demo.admin', 'Diana Constantinescu', 90), ('demo.moderator', 'Răzvan Iacob', 80),
             ('demo.leader', 'Andreea Lupu', 70), ('demo.student', 'Matei Dumitrescu', 60),
             ('cosmin.vlad', 'Cosmin Vlad', 6), ('bianca.ene', 'Bianca Ene', 5), ('eduard.sima', 'Eduard Sima', 4),
             ('paula.cojocaru', 'Paula Cojocaru', 3), ('lucian.grigore', 'Lucian Grigore', 2)) r(email, display_name, age);

UPDATE dm_member m SET user_id = a.id FROM core.user_account a WHERE a.email = m.email || '@student.ugal.ro';

INSERT INTO core.user_role (user_id, role_code)
SELECT id, substr(email, 6, strpos(email, '@') - 6) FROM core.user_account
WHERE email IN ('demo.admin@student.ugal.ro', 'demo.moderator@student.ugal.ro');

INSERT INTO core.group_membership (user_id, group_id, subgroup_id, kind, source, valid_during)
SELECT m.user_id, m.group_id, sg.id, 'regular', 'university_sync', m.during
FROM dm_member m
JOIN core.subgroup sg ON sg.group_id = m.group_id AND sg.label = (1 + m.seat % 2)::text
UNION ALL
SELECT a.id, g.id, sg.id, 'regular', 'university_sync', g.during
FROM dm_group g
JOIN core.subgroup sg ON sg.group_id = g.id AND sg.label = '1'
CROSS JOIN core.user_account a
WHERE g.code = '24C31A' AND g.y = 2026 AND a.email IN ('demo.leader@student.ugal.ro', 'demo.student@student.ugal.ro');

INSERT INTO core.membership_request (user_id, group_id, subgroup_id, kind)
SELECT a.id, g.id, sg.id, r.kind
FROM (VALUES ('cosmin.vlad', '24C31A', 'regular'), ('bianca.ene', '24C31A', 'regular'), ('eduard.sima', '24C31A', 'repeat'),
             ('paula.cojocaru', '25C21A', 'regular'), ('lucian.grigore', '25C21A', 'regular')) r(email, code, kind)
JOIN core.user_account a ON a.email = r.email || '@student.ugal.ro'
JOIN dm_group g ON g.code = r.code AND g.y = 2026
JOIN core.subgroup sg ON sg.group_id = g.id AND sg.label = '2';

INSERT INTO core.group_leader_claim (user_id, group_id, status, decided_by, decided_at)
SELECT m.user_id, m.group_id, 'approved', (SELECT id FROM core.user_account WHERE email = 'demo.admin@student.ugal.ro'), now()
FROM dm_member m
JOIN dm_group g ON g.id = m.group_id
WHERE m.seat = 1 AND g.code <> '24C31A'
UNION ALL
SELECT a.id, g.id, 'approved', (SELECT id FROM core.user_account WHERE email = 'demo.admin@student.ugal.ro'), now()
FROM dm_group g CROSS JOIN core.user_account a
WHERE g.code = '24C31A' AND g.y = 2026 AND a.email = 'demo.leader@student.ugal.ro';

INSERT INTO core.group_leader (membership_id, claim_id)
SELECT m.id, c.id
FROM core.group_leader_claim c
JOIN dm_group g ON g.id = c.group_id
JOIN core.group_membership m ON m.user_id = c.user_id AND m.group_id = c.group_id;

INSERT INTO core.group_leader_claim (user_id, group_id)
SELECT m.user_id, m.group_id FROM dm_member m JOIN dm_group g ON g.id = m.group_id WHERE g.code = '25C21A' AND m.seat = 2;

INSERT INTO core.group_invite (group_id, token_hash, expires_at, max_uses, created_by)
SELECT g.id, sha256(convert_to('demo-invite-' || g.code, 'UTF8')), now() + interval '60 days', 30, a.id
FROM dm_group g CROSS JOIN core.user_account a
WHERE g.code = '24C31A' AND g.y = 2026 AND a.email = 'demo.leader@student.ugal.ro';

INSERT INTO anon.offering_key (fingerprint, offering_id, public_key, active)
SELECT sha256(convert_to('demo-key-' || o.id, 'UTF8')), o.id, sha256(convert_to('demo-public-' || o.id, 'UTF8')), false
FROM dm_group g JOIN core.course_offering o ON o.group_id = g.id
WHERE g.y <= 2025;

CREATE TEMP TABLE dm_subject (name text, diff float8, fmt text, hrs float8, strict float8) ON COMMIT DROP;
INSERT INTO dm_subject VALUES
    ('Analiză matematică', 8.6, 'theory', 7, 5), ('Algebră liniară, geometrie analitică și diferențială', 7.4, 'theory', 6, 5),
    ('Matematici speciale', 7.6, 'theory', 6, 5), ('Fizică', 6.6, 'theory', 4, 4), ('Electrotehnica', 7.2, 'theory', 5, 6),
    ('Teoria sistemelor', 7.5, 'theory', 5, 4), ('Teoria probabilităților și statistică matematică', 7.3, 'theory', 5, 4),
    ('Metode numerice', 6.4, 'mixed', 4, 5), ('Matematici discrete', 6.2, 'theory', 4, 4),
    ('Programarea calculatoarelor și limbaje de programare I', 5.8, 'practical', 7, 6),
    ('Programarea calculatoarelor și limbaje de programare II', 6.4, 'practical', 7, 6),
    ('Programarea orientată pe obiecte', 6.2, 'practical', 7, 5), ('Structuri de date și algoritmi', 7.4, 'mixed', 7, 6),
    ('Baze de date I', 6.0, 'mixed', 5, 5), ('Baze de date II', 6.4, 'mixed', 5, 5), ('Sisteme de operare', 6.8, 'mixed', 6, 5),
    ('Rețele de calculatoare I', 6.0, 'mixed', 4, 5), ('Rețele de calculatoare II', 6.2, 'mixed', 4, 5),
    ('Arhitectura sistemelor de calcul', 6.8, 'theory', 5, 4), ('Calculatoare numerice I', 7.0, 'theory', 5, 5),
    ('Calculatoare numerice II', 7.2, 'theory', 5, 5), ('Limbaje formale și translatoare I', 7.8, 'theory', 5, 4),
    ('Limbaje formale și translatoare II', 7.8, 'theory', 5, 4), ('Ingineria programelor', 5.2, 'mixed', 5, 4),
    ('Programare Web', 4.8, 'practical', 6, 6), ('Proiectarea aplicațiilor Web', 4.6, 'practical', 6, 5),
    ('Inteligență artificială', 6.2, 'mixed', 5, 4), ('Învățare automată', 6.6, 'mixed', 6, 4),
    ('Limba engleză (anul 1)', 2.8, 'oral', 1, 6), ('Limba engleză (anul 2)', 2.6, 'oral', 1, 6),
    ('Dispozitive electronice și electronică analogică', 7.0, 'theory', 5, 5), ('Electronică digitală', 6.8, 'mixed', 6, 5),
    ('Grafică asistată de calculator', 4.5, 'practical', 4, 5), ('Informatică aplicată I', 4.8, 'practical', 4, 6),
    ('Prelucrarea imaginilor', 5.8, 'mixed', 5, 4), ('Achiziția și prelucrarea datelor', 6.2, 'mixed', 5, 4),
    ('Proiectarea cu microprocesoare', 7.0, 'mixed', 6, 5), ('Managementul proiectelor software', 4.4, 'practical', 4, 4);

CREATE TEMP TABLE dm_submission ON COMMIT DROP AS
SELECT sha256(convert_to('demo-' || o.id || '-' || r.seat, 'UTF8')) AS key_hash,
       encode(sha256(convert_to('demo-' || o.id || '-' || r.seat, 'UTF8')), 'hex') AS k,
       o.id AS offering_id, sg.id AS subgroup_id, s.name_ro AS subject_name, s.id AS subject_id,
       ci.lab_hours + ci.project_hours > 0 AS has_practice, ci.elective_slot IS NOT NULL AS elective,
       lec.base AS lec_base, prac.base AS prac_base
FROM dm_group g
JOIN core.course_offering o ON o.group_id = g.id
JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
JOIN core.subject s ON s.id = ci.subject_id
CROSS JOIN LATERAL (SELECT greatest(1, round((CASE g.y WHEN 2023 THEN 3 WHEN 2024 THEN 4 ELSE 6 END + 3 * pg_temp.h('resp/' || o.id))
                                              * CASE WHEN ci.elective_slot IS NULL THEN 1 ELSE 0.22 END))::int AS n) cnt
CROSS JOIN LATERAL generate_series(1, cnt.n) r(seat)
JOIN core.subgroup sg ON sg.group_id = g.id AND sg.label = (1 + r.seat % 2)::text
LEFT JOIN LATERAL (SELECT avg(p.base) AS base
                   FROM core.teaching_assignment ta JOIN core.professor pr ON pr.id = ta.professor_id JOIN dm_prof p ON p.slug = pr.slug
                   WHERE ta.offering_id = o.id AND ta.component = 'lecture') lec ON true
LEFT JOIN LATERAL (SELECT avg(p.base) AS base
                   FROM core.teaching_assignment ta JOIN core.professor pr ON pr.id = ta.professor_id JOIN dm_prof p ON p.slug = pr.slug
                   WHERE ta.offering_id = o.id AND ta.component IN ('lab', 'project')
                     AND (ta.subgroup_id IS NULL OR ta.subgroup_id = sg.id)) prac ON true
WHERE g.y <= 2025 AND EXISTS (SELECT 1 FROM core.teaching_assignment ta WHERE ta.offering_id = o.id);

INSERT INTO anon.submission (key_hash, offering_id, key_fingerprint, exam_difficulty, labs_help, attendance_useful,
                             weekly_hours, exam_format, attendance_strictness)
SELECT s.key_hash, s.offering_id, sha256(convert_to('demo-key-' || s.offering_id, 'UTF8')),
       pg_temp.clamp10(coalesce(sp.diff, 4.5 + 2.5 * pg_temp.h('d/' || s.subject_name)) + 1.3 * pg_temp.nz(s.k || '/d')),
       CASE WHEN s.has_practice THEN pg_temp.clamp10(0.9 * coalesce(s.prac_base, s.lec_base, 7) + 1.5 * pg_temp.nz(s.k || '/l') - 0.5) END,
       CASE WHEN pg_temp.h(s.k || '/au') < (coalesce(s.lec_base, 7) - 4.5) / 5 - 0.15 THEN 'yes'
            WHEN pg_temp.h(s.k || '/au') < (coalesce(s.lec_base, 7) - 4.5) / 5 + 0.25 THEN 'partly'
            ELSE 'no' END,
       least(30, greatest(0, round(coalesce(sp.hrs, 2 + 3 * pg_temp.h('w/' || s.subject_name)) + 2 * pg_temp.nz(s.k || '/w'))))::smallint,
       coalesce(sp.fmt, 'mixed'),
       pg_temp.clamp10(coalesce(sp.strict, 3 + 4 * pg_temp.h('s/' || s.subject_name)) + 1.2 * pg_temp.nz(s.k || '/s'))
FROM dm_submission s
LEFT JOIN dm_subject sp ON sp.name = s.subject_name;

INSERT INTO anon.rating (key_hash, teaching_assignment_id, offering_id,
                         a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14)
SELECT t.key_hash, t.ta_id, t.offering_id, t.sc[1], t.sc[2], t.sc[3], t.sc[4], t.sc[5], t.sc[6], t.sc[7], t.sc[8], t.sc[9],
       t.sc[10], t.sc[11], t.sc[12], t.sc[13], t.sc[14]
FROM (SELECT s.key_hash, ta.id AS ta_id, s.offering_id,
             ARRAY(SELECT pg_temp.sc(p.base, p.strong, p.weak, p.slug, u.axis, s.k, ta.id, ta.component, s.subject_name)
                   FROM unnest(ARRAY['a01', 'a02', 'a03', 'a04', 'a05', 'a06', 'a07', 'a08', 'a09', 'a10', 'a11', 'a12', 'a13', 'a14'])
                        WITH ORDINALITY u(axis, no)
                   ORDER BY u.no) AS sc
      FROM dm_submission s
      JOIN core.teaching_assignment ta ON ta.offering_id = s.offering_id AND (ta.subgroup_id IS NULL OR ta.subgroup_id = s.subgroup_id)
      JOIN core.professor pr ON pr.id = ta.professor_id
      JOIN dm_prof p ON p.slug = pr.slug) t
WHERE num_nonnulls(t.sc[1], t.sc[2], t.sc[3], t.sc[4], t.sc[5], t.sc[6], t.sc[7], t.sc[8], t.sc[9], t.sc[10], t.sc[11], t.sc[12],
                   t.sc[13], t.sc[14]) >= 1;

CREATE TEMP TABLE dm_comment_text (cls text, comp text, body text, no integer, cnt integer) ON COMMIT DROP;
INSERT INTO dm_comment_text (cls, comp, body) VALUES
    ('pos', 'lecture', 'Cursurile sunt foarte bine structurate, iar explicațiile merg de la exemple simple la cele complicate. Se înțelege din prima.'),
    ('pos', 'lecture', 'Un profesor care chiar vrea ca studenții să înțeleagă. Răspunde la orice întrebare, chiar și la cele mai simple.'),
    ('pos', 'lecture', 'Materialele de curs sunt complete și actualizate, iar slide-urile se pot folosi direct la învățat pentru examen.'),
    ('pos', 'lecture', 'Ore bine organizate, fără timpi morți. Se vede că își pregătește fiecare curs.'),
    ('pos', 'lecture', 'Notarea este clară încă din prima săptămână și nu s-a schimbat pe parcurs. Exact cum ar trebui să fie peste tot.'),
    ('pos', 'lecture', 'Cursul face legătura cu ce se folosește în practică, nu doar teorie seacă. Mi-a folosit și la practica din vară.'),
    ('pos', 'lecture', 'Foarte respectuos cu studenții și mereu disponibil pe email. Răspunde în aceeași zi.'),
    ('pos', 'lecture', 'Examenul a fost corect, din ce s-a predat la curs. Cine a venit la ore și a rezolvat exercițiile nu a avut surprize.'),
    ('pos', 'lecture', 'Pune accent pe înțelegere, nu pe memorat. Am învățat mai mult aici decât la alte materii cu mai multe ore.'),
    ('pos', 'lecture', 'Cursurile au fost interesante și cu exemple din industrie. Ar merita mai multe ore pe săptămână.'),
    ('pos', 'lecture', 'Explică răbdător și repetă ce nu a fost clar. Punctajele de pe parcurs au fost afișate la timp.'),
    ('pos', 'lecture', 'Una dintre materiile la care chiar merită să mergi la curs. Ritmul e bun și atmosfera relaxată.'),
    ('pos', 'lecture', 'Ne-a dat subiecte de test din anii trecuți și le-a rezolvat împreună cu noi. Foarte de ajutor.'),
    ('pos', 'lecture', 'Organizat, corect și deschis la feedback. După primele săptămâni a ajustat ritmul cum i-am cerut.'),
    ('pos', 'lab', 'Laboratoarele sunt bine legate de curs și se termină cu ceva funcțional. Primești feedback imediat la predare.'),
    ('pos', 'lab', 'Cel mai bun lucru la materia asta sunt orele de laborator. Explică pe loc ce ai greșit, fără să te facă să te simți prost.'),
    ('pos', 'lab', 'Predarea lucrărilor este relaxată și clară: știi de la început ce trebuie să ai ca să iei nota maximă.'),
    ('pos', 'lab', 'Proiectul a fost relevant și ne-a lăsat să alegem tema. Am învățat mult mai mult decât din cursuri.'),
    ('pos', 'lab', 'Mereu disponibil la laborator și după ore. Nu pleacă până nu înțelege toată lumea.'),
    ('pos', 'lab', 'Platforma de laborator funcționa bine, iar enunțurile erau clare. Lucrările se puteau preda și online.'),
    ('pos', 'lab', 'Se vede că știe domeniul. Ne-a arătat și unelte folosite în companii, nu doar ce era în fișă.'),
    ('pos', 'lab', 'Punctajul pe laborator e transparent și se actualizează săptămânal. Nicio surpriză la final.'),
    ('pos', 'lab', 'Atmosferă bună la laborator, poți pune întrebări oricând. Ajută dacă rămâi blocat.'),
    ('pos', 'lab', 'Lucrările practice au fost bine dozate ca dificultate, de la simplu la complex, fără salturi.'),
    ('pos', 'lab', 'Corect la notare și foarte răbdător cu cei care nu au mai făcut așa ceva până acum.'),
    ('pos', 'lab', 'La prezentarea proiectului a contat ce știm să explicăm, nu cât de lung e codul. Evaluare corectă.'),
    ('pos', 'seminar', 'La seminar înțelegi de fapt ce se predă la curs. Rezolvă exerciții pe tablă și le explică pas cu pas.'),
    ('pos', 'seminar', 'Foarte răbdător și deschis la întrebări. Testele de la seminar seamănă cu cele de la examen.'),
    ('pos', 'seminar', 'Seminarele sunt bine dozate, cu probleme graduale și feedback imediat, ceea ce ajută mult.'),
    ('pos', 'seminar', 'Ne-a dat subiecte tip examen și le-am discutat împreună, cu explicații clare.'),
    ('pos', 'seminar', 'Punctajul de la seminar e transparent și se actualizează la zi.'),
    ('pos', 'seminar', 'Atmosferă prietenoasă, nu te simți judecat dacă nu știi ceva.'),
    ('mid', 'lecture', 'Curs ok, dar ritmul e prea alert și slide-urile sunt destul de încărcate. Fără materiale în plus e greu de urmărit.'),
    ('mid', 'lecture', 'Materia e grea, dar profesorul explică corect. Ar ajuta mai multe exemple rezolvate la curs.'),
    ('mid', 'lecture', 'Predă bine, însă punctajele de pe parcurs apar greu pe platformă. Aflăm de multe ori nota abia la final.'),
    ('mid', 'lecture', 'Conținut bun, doar că se simte uneori învechit. Ar merita actualizate exemplele cu ceva mai nou.'),
    ('mid', 'lecture', 'Cursurile sunt utile, dar fără prezență nu te descurci. Nu se înregistrează nimic și nu există suport complet.'),
    ('mid', 'lecture', 'Examenul e corect, dar lung. Dacă nu ai exersat subiectele din anii trecuți, timpul nu îți ajunge.'),
    ('mid', 'lecture', 'Explică clar, dar e mai greu de contactat. Răspunsurile pe email vin după câteva zile.'),
    ('mid', 'lecture', 'Într-un cuvânt, ok. Nu iese în evidență, dar nici nu ai probleme dacă înveți constant.'),
    ('mid', 'lecture', 'Regulile de notare au mai fost ajustate pe parcurs. Ar fi bine să fie fixate din prima săptămână.'),
    ('mid', 'lecture', 'Teoria e bine predată, dar legătura cu aplicațiile reale lipsește. Aș fi vrut mai multe exemple practice.'),
    ('mid', 'lecture', 'Bun profesor, doar că se grăbește spre finalul semestrului și rămân lucruri nespuse.'),
    ('mid', 'lecture', 'Notarea e rezonabilă, dar baremul nu e făcut public dinainte. Ar fi bine să știm ce se punctează.'),
    ('mid', 'lab', 'Laboratoarele sunt utile, dar enunțurile ar putea fi mai clare. De multe ori pierdem timp să înțelegem ce se cere.'),
    ('mid', 'lab', 'Predarea lucrărilor merge ok, dar uneori se aglomerează și rămâne puțin timp pentru fiecare student.'),
    ('mid', 'lab', 'Corect la notare, însă feedbackul vine târziu. Aflăm ce am greșit abia după câteva săptămâni.'),
    ('mid', 'lab', 'Proiectul e interesant, dar termenele sunt strânse. Cine începe devreme o duce bine.'),
    ('mid', 'lab', 'Ar fi nevoie de mai multă legătură între laborator și curs. Uneori faci lucruri pe care nu le-ai văzut la teorie.'),
    ('mid', 'lab', 'Atmosferă ok, dar calculatoarele din laborator au avut probleme tehnice mai multe ore.'),
    ('mid', 'lab', 'Ajută dacă îl întrebi, dar nu explică din proprie inițiativă. Trebuie să fii activ.'),
    ('mid', 'lab', 'Lucrări potrivite ca dificultate. Mi-aș fi dorit mai multă flexibilitate la termenele de predare.'),
    ('mid', 'lab', 'Punctajul pe laborator nu a fost afișat la zi, dar la final totul a fost corect.'),
    ('mid', 'lab', 'Se învață lucruri utile, dar evaluarea e cam subiectivă și diferă de la grupă la grupă.'),
    ('mid', 'seminar', 'Seminarul ajută la înțelegerea cursului, dar ritmul e prea rapid pentru cine nu a ținut pasul.'),
    ('mid', 'seminar', 'Exercițiile sunt bune, dar ar trebui mai multe rezolvate pe tablă înainte să fim lăsați singuri.'),
    ('mid', 'seminar', 'Fără seminar nu înțelegi cursul, dar nici nu e cel mai bine structurat.'),
    ('mid', 'seminar', 'Corect la prezență și la teste, însă explicațiile sunt uneori prea scurte.'),
    ('mid', 'seminar', 'Util, mai ales înainte de examen. Aș vrea mai multe probleme tip examen.'),
    ('neg', 'lecture', 'Cursul este greu de urmărit, iar materialele sunt insuficiente. Am învățat practic doar din culegere și de pe internet.'),
    ('neg', 'lecture', 'Criteriile de notare nu sunt clare, iar baremul de la examen a părut diferit de ce s-a cerut la curs.'),
    ('neg', 'lecture', 'Predare monotonă, citește aproape tot din slide-uri. Fără exemple e greu să rămâi atent.'),
    ('neg', 'lecture', 'Tehnologiile prezentate sunt depășite și nu au legătură cu ce se cere azi pe piață. Ar trebui actualizat cursul.'),
    ('neg', 'lecture', 'Disponibilitatea e minimă: emailurile rămân fără răspuns și consultațiile sunt rare.'),
    ('neg', 'lecture', 'Regulile s-au schimbat după primele evaluări, iar punctajele nu au fost afișate până la examen.'),
    ('neg', 'lecture', 'Examenul e mult mai greu decât ce s-a predat la ore. Ratele de promovare sunt mici la prima sesiune.'),
    ('neg', 'lecture', 'Ai nevoie de prezență, dar cursul nu aduce mare valoare. Mai bine înveți singur din cartea recomandată.'),
    ('neg', 'lecture', 'Tonul față de studenți a fost uneori sec, iar întrebările nu erau primite cu răbdare.'),
    ('neg', 'lab', 'Laboratoarele nu se leagă de curs și enunțurile sunt vagi. Am aflat ce trebuia să facem abia la predare.'),
    ('neg', 'lab', 'Predarea este stresantă, iar notarea pare diferită de la o persoană la alta. Nu există un barem public.'),
    ('neg', 'lab', 'Feedback aproape inexistent. Primești o notă fără să afli ce trebuia corectat.'),
    ('neg', 'lab', 'Prea multe lucrări într-un interval scurt, iar suportul din partea cadrului didactic e redus.'),
    ('neg', 'lab', 'Termenele au fost mutate de mai multe ori și nu s-au anunțat din timp, ceea ce a încurcat organizarea.'),
    ('neg', 'lab', 'Lucrările sunt demodate, bazate pe unelte pe care nu le mai folosește nimeni.'),
    ('neg', 'lab', 'Atmosfera la laborator e tensionată. Te gândești de două ori înainte să pui o întrebare.'),
    ('neg', 'lab', 'Nu a ajutat când am rămas blocat, a spus doar să mă uit în curs.'),
    ('neg', 'seminar', 'Seminarul e doar recitat din curs și nu rezolvăm exerciții noi. Fără culegere nu ai cum să te pregătești.'),
    ('neg', 'seminar', 'Notele la teste vin târziu și baremul nu e explicat, nu știi niciodată ce ai greșit.'),
    ('neg', 'seminar', 'Ritm prea rapid, răspunsurile la întrebări sunt scurte și nu se mai revine asupra lor.'),
    ('neg', 'seminar', 'Seminarele s-au anulat de mai multe ori și nu s-au recuperat, iar examenul a cerut tot ce era în programă.');
UPDATE dm_comment_text t SET no = n.no, cnt = n.cnt
FROM (SELECT ctid AS id, row_number() OVER (PARTITION BY cls, comp ORDER BY md5(body)) AS no, count(*) OVER (PARTITION BY cls, comp) AS cnt
      FROM dm_comment_text) n
WHERE t.ctid = n.id;

CREATE TEMP TABLE dm_tip_text (subject text, flag text, body text, id integer GENERATED ALWAYS AS IDENTITY) ON COMMIT DROP;
INSERT INTO dm_tip_text (subject, flag, body) VALUES
    (NULL, 'any', 'Prezența la primele cursuri contează: se explică regulile de notare și ce se cere la examen.'),
    (NULL, 'any', 'Începe să înveți cu cel puțin două săptămâni înainte de sesiune, nu cu o noapte înainte.'),
    (NULL, 'any', 'Fă toate temele din timpul semestrului, de multe ori sunt baza subiectelor de examen.'),
    (NULL, 'any', 'Cere de la colegii din anii mai mari subiectele din anii trecuți și rezolvă-le cronometrat.'),
    (NULL, 'any', 'Grupul de WhatsApp al anului e salvarea: acolo se anunță schimbările de orar și termenele.'),
    (NULL, 'any', 'Verifică des platforma de curs, punctajele și anunțurile apar uneori fără notificare.'),
    (NULL, 'any', 'Dacă nu înțelegi ceva, întreabă la curs sau la consultații. Profesorii răspund mai des decât te aștepți.'),
    (NULL, 'any', 'Păstrează toate materialele într-un singur loc, ca să nu le cauți în ultima seară înainte de examen.'),
    (NULL, 'any', 'Nu te baza doar pe slide-uri, ia notițe la curs. Multe explicații nu sunt în materialele publicate.'),
    (NULL, 'any', 'Învățați în grup: explicând unui coleg verifici ce ai înțeles de fapt.'),
    (NULL, 'any', 'Ia în serios consultațiile dinaintea examenului, se mai dau indicii despre ce se cere.'),
    (NULL, 'any', 'Nu amâna restanțele: se adună și strică sesiunea următoare.'),
    (NULL, 'any', 'Citește atent enunțurile la examen, multe puncte se pierd din grabă și nu din necunoaștere.'),
    (NULL, 'any', 'Alocă câteva ore pe săptămână pentru materia asta din primele săptămâni, nu se poate recupera într-o noapte.'),
    (NULL, 'project', 'Nu lăsa proiectul pe ultima săptămână. Cei care au început devreme au predat fără stres.'),
    (NULL, 'project', 'Alege tema din prima săptămână și discut-o cu cadrul didactic înainte să te apuci, ca să nu o refaci.'),
    (NULL, 'project', 'Folosește Git de la început și fă commit-uri dese, e mai ușor de arătat ce ai făcut la predare.'),
    (NULL, 'project', 'Lucrează în echipă doar cu cine chiar vrea să contribuie, altfel proiectul rămâne pe umerii unuia singur.'),
    (NULL, 'project', 'Pregătește un demo funcțional înainte de prezentare: o aplicație care pornește contează mai mult decât slide-urile.'),
    (NULL, 'lab', 'Rulează-ți laboratoarele acasă înainte de ora de predare, în laborator se aglomerează repede.'),
    (NULL, 'lab', 'Termină fișa de laborator în timpul orei. Ce amâni se adună, iar la final nu mai ai timp.'),
    (NULL, 'lab', 'Păstrează codul de la laboratoare, îți folosește la examenul practic.'),
    (NULL, 'lab', 'Vino cu laptopul încărcat și mediul de lucru instalat, se pierde mult timp cu configurarea.'),
    (NULL, 'lab', 'Predă lucrările la zi, recuperarea la final e mereu mai grea.'),
    ('Analiză matematică', 'any', 'Fă toate seminarele și culegerea, examenul seamănă cu problemele rezolvate acolo. Teoria fără exerciții nu ajunge.'),
    ('Analiză matematică', 'any', 'Învață demonstrațiile de la curs: se cer la examen și valorează multe puncte.'),
    ('Structuri de date și algoritmi', 'any', 'Implementează fiecare structură de la zero cel puțin o dată, nu doar citind. La examen se cere cod, nu doar teorie.'),
    ('Structuri de date și algoritmi', 'any', 'Exersează complexitatea algoritmilor pe hârtie, sunt întrebări sigure la examen.'),
    ('Baze de date I', 'any', 'Exersează interogări SQL pe date reale, nu doar pe cele din laborator. Joinurile complexe se cer la examen.'),
    ('Baze de date II', 'any', 'Citește planurile de execuție, ajută la întrebările de optimizare de la examen.'),
    ('Programarea orientată pe obiecte', 'any', 'Scrie cod în fiecare săptămână. Proiectul final nu se poate face într-o singură noapte.'),
    ('Programarea orientată pe obiecte', 'any', 'Înțelege bine diferența dintre moștenire și compoziție, apare mereu la examen.'),
    ('Programarea calculatoarelor și limbaje de programare I', 'any', 'Dacă nu ai mai programat, începe din prima zi cu exerciții simple. Se recuperează greu dacă pierzi primele săptămâni.'),
    ('Programarea calculatoarelor și limbaje de programare II', 'any', 'Pointerii și memoria dinamică sunt partea grea. Exersează-le înainte de colocviu.'),
    ('Sisteme de operare', 'any', 'Fă laboratoarele pe un Linux real sau pe o mașină virtuală, nu doar din documentație.'),
    ('Rețele de calculatoare I', 'any', 'Învață adresarea IP și subnetting-ul, sunt exerciții sigure la examen.'),
    ('Programare Web', 'any', 'Începe proiectul din timp și testează-l și pe telefon. Se apreciază aplicațiile care chiar rulează.'),
    ('Proiectarea aplicațiilor Web', 'any', 'Stabilește din primele zile arhitectura și împărțirea sarcinilor în echipă, altfel pierzi timp la integrare.'),
    ('Inteligență artificială', 'any', 'Rezolvă exercițiile de la seminar pe hârtie, apoi implementează-le. Se cer ambele la examen.'),
    ('Limbaje formale și translatoare I', 'any', 'Rezolvă multe exerciții cu automate și gramatici. Teoria are sens abia după ce faci exemple.'),
    ('Fizică', 'any', 'Învață formulele împreună cu unitățile de măsură, o bună parte din greșeli vin de acolo.'),
    ('Teoria sistemelor', 'any', 'Fă toate problemele de la laborator, examenul seamănă mult cu ele.'),
    ('Electrotehnica', 'any', 'Prinde bine principiile de bază din primele cursuri, restul semestrului se construiește pe ele.'),
    ('Metode numerice', 'any', 'Implementează metodele în limbajul preferat și verifică rezultatele cu un exemplu făcut de mână.'),
    ('Limba engleză (anul 1)', 'any', 'Pregătește-te din timp pentru partea orală, vorbitul se exersează, nu se învață peste noapte.'),
    ('Ingineria programelor', 'any', 'Ia în serios documentația proiectului, se punctează la fel de mult ca și codul.'),
    ('Calculatoare numerice I', 'any', 'Notează tot de la curs, materialele publicate sunt puține și nu acoperă tot.'),
    ('Programare logică și programare funcțională', 'any', 'Prolog cere o altă mentalitate. Încearcă să rezolvi exercițiile fără să gândești imperativ.');

CREATE TEMP TABLE dm_comment ON COMMIT DROP AS
SELECT c.key_hash, c.offering_id, c.ta_id, c.professor_id, c.cls, c.rn, t.body
FROM (SELECT r.key_hash, r.offering_id, r.teaching_assignment_id AS ta_id, ta.professor_id,
             CASE WHEN ta.component = 'lecture' THEN 'lecture' WHEN ta.component = 'seminar' THEN 'seminar' ELSE 'lab' END AS comp,
             CASE WHEN m.mean >= 8 THEN 'pos' WHEN m.mean >= 6.3 THEN 'mid' ELSE 'neg' END AS cls,
             row_number() OVER (PARTITION BY ta.professor_id, ci.subject_id,
                                             CASE WHEN m.mean >= 8 THEN 'pos' WHEN m.mean >= 6.3 THEN 'mid' ELSE 'neg' END,
                                             ta.component = 'lecture'
                                ORDER BY o.id, md5(encode(r.key_hash, 'hex'))) AS rn
      FROM anon.rating r
      JOIN core.teaching_assignment ta ON ta.id = r.teaching_assignment_id
      JOIN core.course_offering o ON o.id = r.offering_id
      JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
      CROSS JOIN LATERAL (SELECT (coalesce(r.a01, 0) + coalesce(r.a02, 0) + coalesce(r.a03, 0) + coalesce(r.a04, 0) + coalesce(r.a05, 0)
                                  + coalesce(r.a06, 0) + coalesce(r.a07, 0) + coalesce(r.a08, 0) + coalesce(r.a09, 0) + coalesce(r.a10, 0)
                                  + coalesce(r.a11, 0) + coalesce(r.a12, 0) + coalesce(r.a13, 0) + coalesce(r.a14, 0))::float8
                                 / num_nonnulls(r.a01, r.a02, r.a03, r.a04, r.a05, r.a06, r.a07, r.a08, r.a09, r.a10, r.a11, r.a12,
                                                r.a13, r.a14) AS mean) m
      WHERE pg_temp.h(encode(r.key_hash, 'hex') || '/' || ta.id || '/c') < 0.12) c
JOIN LATERAL (SELECT x.body, x.no, x.cnt FROM dm_comment_text x WHERE x.cls = c.cls AND x.comp = c.comp) t
     ON t.no = 1 + (c.rn - 1 + c.professor_id * 3) % t.cnt;

CREATE TEMP TABLE dm_tip ON COMMIT DROP AS
SELECT c.key_hash, c.offering_id, t.body
FROM (SELECT s.key_hash, s.offering_id, s.subject_name, s.has_practice,
             EXISTS (SELECT 1 FROM core.course_offering o JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
                     WHERE o.id = s.offering_id AND ci.project_hours > 0) AS has_project,
             row_number() OVER (PARTITION BY s.subject_id ORDER BY s.offering_id, md5(s.k)) AS rn
      FROM dm_submission s
      WHERE pg_temp.h(s.k || '/t') < 0.14) c
JOIN LATERAL (SELECT x.body, row_number() OVER (ORDER BY (x.subject IS NULL), md5(c.subject_name || x.id)) AS no, count(*) OVER () AS cnt
              FROM dm_tip_text x
              WHERE x.subject = c.subject_name
                 OR (x.subject IS NULL AND (x.flag = 'any' OR (x.flag = 'project' AND c.has_project) OR (x.flag = 'lab' AND c.has_practice)))) t
     ON t.no = 1 + (c.rn - 1) % t.cnt;

CREATE TEMP TABLE dm_post ON COMMIT DROP AS
SELECT p.*, row_number() OVER (PARTITION BY p.kind ORDER BY md5(p.id::text)) AS slot
FROM (SELECT md5('demo-comment-' || encode(key_hash, 'hex') || '-' || ta_id)::uuid AS id, key_hash, offering_id, 'comment' AS kind,
             ta_id, body, cls
      FROM dm_comment
      UNION ALL
      SELECT md5('demo-tip-' || encode(key_hash, 'hex'))::uuid, key_hash, offering_id, 'tip', NULL, body, NULL
      FROM dm_tip) p;

INSERT INTO anon.post (id, key_hash, offering_id, kind, teaching_assignment_id)
SELECT id, key_hash, offering_id, kind, ta_id FROM dm_post;

INSERT INTO anon.post_version (post_id, version_no, kind, body, status, reason_code, submitted_week, moderated_week)
SELECT p.id, 1, p.kind,
       CASE WHEN p.status = 'rejected' THEN 'Când se afișează notele finale? Nu am găsit nimic pe platformă și nici la avizier.' ELSE p.body END,
       p.status, CASE WHEN p.status = 'rejected' THEN 'off_topic' END,
       CASE WHEN p.status = 'pending' THEN date_trunc('week', current_date)::date - 7 * (p.slot % 3)::int ELSE p.week - 7 END,
       CASE WHEN p.status <> 'pending' THEN p.week END
FROM (SELECT d.*,
             CASE WHEN d.kind = 'comment' AND d.slot <= 6 THEN 'pending'
                  WHEN d.kind = 'comment' AND d.slot <= 8 THEN 'rejected'
                  WHEN d.kind = 'tip' AND d.slot <= 2 THEN 'pending'
                  ELSE 'approved' END AS status,
             date_trunc('week', ov.rating_opens_at + make_interval(days => 7 + floor(40 * pg_temp.h(d.id::text))::int))::date AS week
      FROM dm_post d
      JOIN core.offering_v ov ON ov.offering_id = d.offering_id) p;

INSERT INTO anon.post_report (post_id, reason_code, reported_week, status)
SELECT d.id, r.reason, date_trunc('week', current_date)::date, 'open'
FROM (SELECT id, cls, row_number() OVER (PARTITION BY cls ORDER BY md5(id::text || 'rep')) AS rn
      FROM dm_post WHERE kind = 'comment' AND slot > 8 AND cls = 'neg') d
JOIN (VALUES ('neg', 'accusation', 1), ('neg', 'false', 2)) r(cls, reason, rn) ON r.cls = d.cls AND r.rn = d.rn;

ANALYZE anon.submission, anon.rating, anon.post, anon.post_version, core.teaching_assignment, core.course_offering;

REFRESH MATERIALIZED VIEW anon.prof_subject_radar;
REFRESH MATERIALIZED VIEW anon.course_survival;
--rollback SELECT 1;
