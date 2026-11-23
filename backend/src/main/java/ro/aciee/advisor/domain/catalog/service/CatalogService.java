package ro.aciee.advisor.domain.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.catalog.model.CatalogItem;
import ro.aciee.advisor.domain.catalog.model.CoursePage;
import ro.aciee.advisor.domain.catalog.model.CourseTeacher;
import ro.aciee.advisor.domain.catalog.model.Lookups;
import ro.aciee.advisor.domain.catalog.model.Option;
import ro.aciee.advisor.domain.catalog.model.PlanOption;
import ro.aciee.advisor.domain.catalog.model.ProfessorListItem;
import ro.aciee.advisor.domain.catalog.model.ProfessorPage;
import ro.aciee.advisor.domain.catalog.model.ProfessorSubject;
import ro.aciee.advisor.domain.catalog.model.PublishedText;
import ro.aciee.advisor.domain.catalog.model.Radar;
import ro.aciee.advisor.domain.catalog.model.Specialization;
import ro.aciee.advisor.domain.catalog.model.Stats;
import ro.aciee.advisor.domain.catalog.model.Survival;
import ro.aciee.advisor.domain.catalog.model.YearOption;

import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final String SUBJECT_OFFERINGS = """
            SELECT o.* FROM core.offering_v o
            JOIN core.subject_root_v sr ON sr.subject_id = o.subject_id
            WHERE sr.root_id = :root""";

    private static final String AXES = "a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14";

    private static final String RADAR_COLUMNS = "n, ARRAY[" + AXES + "] AS scores";

    private static final String PROFESSOR_SCORES = """
            SELECT cell.professor_id, rated.ratings,
                   sum(cell.n * cell.score) / sum(cell.n) AS overall,
                   sum(cell.n * cell.score) FILTER (WHERE cell.pillar = 'teaching') / sum(cell.n) FILTER (WHERE cell.pillar = 'teaching') AS teaching,
                   sum(cell.n * cell.score) FILTER (WHERE cell.pillar = 'practice') / sum(cell.n) FILTER (WHERE cell.pillar = 'practice') AS practice,
                   sum(cell.n * cell.score) FILTER (WHERE cell.pillar = 'grading') / sum(cell.n) FILTER (WHERE cell.pillar = 'grading') AS grading
            FROM (SELECT r.professor_id, r.n, ax.pillar_code AS pillar, u.score
                  FROM anon.prof_subject_radar r
                  CROSS JOIN LATERAL unnest(ARRAY[%1$s]) WITH ORDINALITY u(score, no)
                  JOIN ref.axis ax ON ax.code = 'a' || lpad(u.no::text, 2, '0')
                  WHERE u.score IS NOT NULL) cell
            JOIN (SELECT professor_id, sum(n) AS ratings FROM anon.prof_subject_radar
                  WHERE num_nonnulls(%1$s) > 0 GROUP BY professor_id) rated ON rated.professor_id = cell.professor_id
            GROUP BY cell.professor_id, rated.ratings""".formatted(AXES);

    private static final RowMapper<Radar> RADAR = (rs, row) -> {
        var radar = new Radar();
        radar.setN(rs.getInt("n"));
        radar.setScores(Arrays.stream((Object[]) rs.getArray("scores").getArray())
                .map(score -> score == null ? null : ((Number) score).doubleValue())
                .toList());
        return radar;
    };

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public Stats stats() {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM core.user_account a WHERE a.disabled_at IS NULL
                                AND NOT EXISTS (SELECT 1 FROM core.email_verification v WHERE v.user_id = a.id)) AS students,
                               (SELECT coalesce(sum(n), 0) FROM anon.course_survival) AS ratings""")
                .query(Stats.class)
                .single();
    }

    @Transactional(readOnly = true)
    public Lookups lookups() {
        var lookups = new Lookups();
        lookups.setAxes(options("SELECT code, label_ro AS label, pillar_code AS \"group\" FROM ref.axis ORDER BY code"));
        lookups.setPillars(options("SELECT code, label_ro AS label FROM ref.pillar ORDER BY no"));
        lookups.setExamFormats(options("SELECT code, label_ro AS label FROM ref.exam_format ORDER BY label_ro"));
        lookups.setAttendance(options("SELECT code, label_ro AS label FROM ref.attendance_usefulness"));
        lookups.setReportReasons(options("SELECT code, label_ro AS label FROM ref.report_reason"));
        lookups.setModerationReasons(options("SELECT code, label_ro AS label FROM ref.moderation_reason"));
        lookups.setThreshold(threshold());
        return lookups;
    }

    @Transactional(readOnly = true)
    public List<Specialization> specializations() {
        var specializations = jdbc.sql("SELECT id, code, name_ro AS name, degree, duration_years FROM core.specialization ORDER BY degree, name_ro")
                .query(Specialization.class)
                .list();
        Map<Integer, List<PlanOption>> plans = jdbc.sql("""
                        SELECT id, specialization_id, lower(intake) AS intake_from, upper(intake) AS intake_to
                        FROM core.study_plan ORDER BY lower(intake) DESC""")
                .query((rs, row) -> {
                    var plan = new PlanOption();
                    plan.setId(rs.getInt("id"));
                    plan.setIntakeFrom(rs.getInt("intake_from"));
                    plan.setIntakeTo(rs.getObject("intake_to", Integer.class));
                    return Map.entry(rs.getInt("specialization_id"), plan);
                })
                .list()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        specializations.forEach(s -> s.setPlans(plans.getOrDefault(s.getId(), List.of())));
        return specializations;
    }

    @Transactional(readOnly = true)
    public List<CatalogItem> items(int planId, int studyYear) {
        return jdbc.sql("""
                        SELECT ci.id, s.name_ro AS subject_name, ci.semester_no, ci.elective_slot,
                               ci.lecture_hours, ci.seminar_hours, ci.lab_hours, ci.project_hours
                        FROM core.curriculum_item ci JOIN core.subject s ON s.id = ci.subject_id
                        WHERE ci.plan_id = :plan AND ci.study_year = :year
                        ORDER BY ci.semester_no, ci.elective_slot NULLS FIRST, s.name_ro""")
                .param("plan", planId)
                .param("year", studyYear)
                .query(CatalogItem.class)
                .list();
    }

    @Transactional(readOnly = true)
    public CoursePage course(int itemId, Integer academicYearId) {
        var page = jdbc.sql("""
                        SELECT ci.id AS item_id, sr.root_id AS root_subject_id, s.name_ro AS subject_name, sp.name_ro AS specialization,
                               ci.study_year, ci.semester_no, ci.plan_id
                        FROM core.curriculum_item ci
                        JOIN core.subject s ON s.id = ci.subject_id
                        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
                        JOIN core.study_plan pl ON pl.id = ci.plan_id
                        JOIN core.specialization sp ON sp.id = pl.specialization_id
                        WHERE ci.id = :item""")
                .param("item", itemId)
                .query(CoursePage.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        int root = page.getRootSubjectId();
        page.setThreshold(threshold());
        page.setYears(jdbc.sql("SELECT DISTINCT g.academic_year_id AS id, g.academic_year AS label FROM (" + SUBJECT_OFFERINGS + """
                        ) o JOIN core.group_v g ON g.id = o.group_id ORDER BY label DESC""")
                .param("root", root)
                .query(YearOption.class)
                .list());
        page.setAcademicYearId(academicYearId != null ? academicYearId : page.getYears().stream().findFirst().map(YearOption::getId).orElse(null));
        page.setTeachers(jdbc.sql("""
                        SELECT p.id AS professor_id, p.slug, p.full_name, t.label_ro AS title, ta.component, c.label_ro AS component_label,
                               string_agg(DISTINCT sg.label, ', ') AS subgroups
                        FROM (""" + SUBJECT_OFFERINGS + """
                        ) o
                        JOIN core.teaching_assignment ta ON ta.offering_id = o.offering_id
                        JOIN core.professor p ON p.id = ta.professor_id
                        JOIN ref.academic_title t ON t.code = p.title_code
                        JOIN ref.component c ON c.code = ta.component
                        LEFT JOIN core.subgroup sg ON sg.id = ta.subgroup_id
                        WHERE o.academic_year_id = :year
                        GROUP BY p.id, p.slug, p.full_name, t.label_ro, ta.component, c.label_ro
                        ORDER BY array_position(ARRAY['lecture', 'seminar', 'lab', 'project'], ta.component), p.full_name""")
                .param("root", root)
                .param("year", page.getAcademicYearId(), Types.INTEGER)
                .query(CourseTeacher.class)
                .list());
        page.setOfferingIds(jdbc.sql("SELECT offering_id FROM (" + SUBJECT_OFFERINGS + ") o").param("root", root).query(Integer.class).list());
        Map<Integer, Radar> radars = radars("subject_id = :key", "professor_id", root);
        Map<Integer, List<PublishedText>> comments = comments("sr.root_id = :key", "ta.professor_id", root);
        page.getTeachers().forEach(teacher -> {
            teacher.setRadar(radars.get(teacher.getProfessorId()));
            teacher.setComments(comments.getOrDefault(teacher.getProfessorId(), List.of()));
        });
        page.setSurvival(jdbc.sql("""
                        SELECT cs.n, cs.exam_difficulty, cs.labs_help, cs.attendance_strictness, cs.weekly_hours,
                               au.label_ro AS attendance_useful, ef.label_ro AS exam_format
                        FROM anon.course_survival cs
                        LEFT JOIN ref.attendance_usefulness au ON au.code = cs.attendance_useful
                        LEFT JOIN ref.exam_format ef ON ef.code = cs.exam_format
                        WHERE cs.subject_id = :root""")
                .param("root", root)
                .query(Survival.class)
                .optional()
                .orElse(null));
        page.setTips(jdbc.sql("""
                        SELECT pp.id, pp.body, pp.moderated_week AS week
                        FROM anon.published_post pp
                        JOIN (""" + SUBJECT_OFFERINGS + """
                        ) o ON o.offering_id = pp.offering_id
                        WHERE pp.kind = 'tip'
                        ORDER BY pp.moderated_week DESC, pp.id""")
                .param("root", root)
                .query(PublishedText.class)
                .list());
        return page;
    }

    @Transactional(readOnly = true)
    public ProfessorPage professor(String slug) {
        var page = jdbc.sql("""
                        SELECT p.id, p.full_name, t.label_ro AS title, d.name_ro AS department, p.archived_at IS NOT NULL AS archived,
                               pr.bio_ro, pr.bio_en, pr.photo, pr.cv_url,
                               coalesce(sc.ratings, 0) AS ratings, sc.overall, sc.teaching, sc.practice, sc.grading
                        FROM core.professor p
                        JOIN ref.academic_title t ON t.code = p.title_code
                        JOIN core.department d ON d.id = p.department_id
                        LEFT JOIN core.professor_profile pr ON pr.name_key = core.name_key(p.full_name)
                        LEFT JOIN (""" + PROFESSOR_SCORES + """
                        ) sc ON sc.professor_id = p.id
                        WHERE p.slug = :slug""")
                .param("slug", slug)
                .query(ProfessorPage.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        page.setThreshold(threshold());
        page.setSubjects(jdbc.sql("""
                        SELECT sr.root_id AS subject_id, root.name_ro AS subject_name,
                               (array_agg(ci.id ORDER BY ay.during DESC))[1] AS item_id,
                               string_agg(DISTINCT c.label_ro, ', ') AS components,
                               extract(YEAR FROM min(lower(ay.during)))::int AS first_year,
                               extract(YEAR FROM max(upper(ay.during)))::int AS last_year
                        FROM core.teaching_assignment ta
                        JOIN core.course_offering o ON o.id = ta.offering_id
                        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
                        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
                        JOIN core.subject root ON root.id = sr.root_id
                        JOIN core.study_group g ON g.id = o.group_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        JOIN ref.component c ON c.code = ta.component
                        WHERE ta.professor_id = :professor
                        GROUP BY sr.root_id, root.name_ro
                        ORDER BY root.name_ro""")
                .param("professor", page.getId())
                .query(ProfessorSubject.class)
                .list());
        Map<Integer, Radar> radars = radars("professor_id = :key", "subject_id", page.getId());
        Map<Integer, List<PublishedText>> comments = comments("ta.professor_id = :key", "sr.root_id", page.getId());
        page.getSubjects().forEach(subject -> {
            subject.setRadar(radars.get(subject.getSubjectId()));
            subject.setComments(comments.getOrDefault(subject.getSubjectId(), List.of()));
        });
        if (page.getOverall() != null) {
            var radar = new Radar();
            radar.setN(page.getRatings());
            radar.setScores(jdbc.sql("""
                            SELECT sum(r.n * u.score) / nullif(sum(r.n) FILTER (WHERE u.score IS NOT NULL), 0)
                            FROM anon.prof_subject_radar r
                            CROSS JOIN LATERAL unnest(ARRAY[%s]) WITH ORDINALITY u(score, no)
                            WHERE r.professor_id = :professor
                            GROUP BY u.no
                            ORDER BY u.no""".formatted(AXES))
                    .param("professor", page.getId())
                    .query((rs, row) -> rs.getObject(1) == null ? null : rs.getDouble(1))
                    .list());
            page.setRadar(radar);
        }
        return page;
    }

    @Transactional(readOnly = true)
    public List<ProfessorListItem> professors() {
        return jdbc.sql("""
                        SELECT p.slug, p.full_name, t.label_ro AS title, d.name_ro AS department, pr.photo,
                               taught.subjects, coalesce(sc.ratings, 0) AS ratings, sc.overall
                        FROM core.professor p
                        JOIN ref.academic_title t ON t.code = p.title_code
                        JOIN core.department d ON d.id = p.department_id
                        LEFT JOIN core.professor_profile pr ON pr.name_key = core.name_key(p.full_name)
                        CROSS JOIN LATERAL (
                            SELECT count(DISTINCT sr.root_id) AS subjects
                            FROM core.teaching_assignment ta
                            JOIN core.offering_v o ON o.offering_id = ta.offering_id
                            JOIN core.subject_root_v sr ON sr.subject_id = o.subject_id
                            WHERE ta.professor_id = p.id) taught
                        LEFT JOIN (""" + PROFESSOR_SCORES + """
                        ) sc ON sc.professor_id = p.id
                        WHERE p.archived_at IS NULL OR sc.ratings > 0
                        ORDER BY p.full_name""")
                .query(ProfessorListItem.class)
                .list();
    }

    private Map<Integer, Radar> radars(String filter, String keyColumn, int key) {
        return jdbc.sql("SELECT %s AS key, %s FROM anon.prof_subject_radar WHERE %s".formatted(keyColumn, RADAR_COLUMNS, filter))
                .param("key", key)
                .query((rs, row) -> Map.entry(rs.getInt("key"), RADAR.mapRow(rs, row)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<Integer, List<PublishedText>> comments(String filter, String keyColumn, int key) {
        return jdbc.sql("""
                        SELECT %s AS key, pp.id, pp.body, pp.moderated_week AS week
                        FROM anon.published_post pp
                        JOIN core.teaching_assignment ta ON ta.id = pp.teaching_assignment_id
                        JOIN core.course_offering o ON o.id = ta.offering_id
                        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
                        JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id
                        WHERE pp.kind = 'comment' AND %s
                        ORDER BY pp.moderated_week DESC, pp.id""".formatted(keyColumn, filter))
                .param("key", key)
                .query((rs, row) -> {
                    var text = new PublishedText();
                    text.setId(rs.getObject("id", UUID.class));
                    text.setBody(rs.getString("body"));
                    text.setWeek(rs.getObject("week", LocalDate.class));
                    return Map.entry(rs.getInt("key"), text);
                })
                .list()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private List<Option> options(String sql) {
        return jdbc.sql(sql).query(Option.class).list();
    }

    private int threshold() {
        return jdbc.sql("SELECT k_threshold FROM ref.setting").query(Integer.class).single();
    }
}
