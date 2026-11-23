package ro.aciee.advisor.domain.timetable.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import ro.aciee.advisor.domain.admin.service.AdminTableService;
import ro.aciee.advisor.domain.timetable.model.SyncResult;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.token.service.TokenService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableSyncService {

    private static final String SYNCED_SEMESTERS = """
            SELECT s.id FROM core.semester s JOIN core.academic_year ay ON ay.id = s.academic_year_id
            WHERE ay.during @> current_date
               OR EXISTS (SELECT 1 FROM core.study_group g WHERE g.academic_year_id = ay.id AND g.successor_id IS NOT NULL)""";

    private final JdbcClient jdbc;
    private final TimetableService timetableService;
    private final AdminTableService adminTableService;
    private final TeacherImportService teacherImportService;
    private final TokenService tokenService;
    private final AtomicBoolean keysRunning = new AtomicBoolean();
    private final Set<String> archivesRead = ConcurrentHashMap.newKeySet();

    public SyncResult sync() {
        var programs = timetableService.current().getPrograms();
        var result = sync(programs);
        int pastTeachers = importPastTeachers(programs);
        return new SyncResult(result.groups(), result.pastGroups(), result.courses(), result.teachers() + pastTeachers,
                result.keysPending());
    }

    public synchronized SyncResult sync(List<TimetableProgram> programs) {
        int groups = programs.stream().mapToInt(this::createGroups).sum();
        int pastGroups = createPastGroups();
        int courses = jdbc.sql("""
                        SELECT g.id FROM core.study_group g JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE (ay.during @> current_date OR g.successor_id IS NOT NULL)
                          AND NOT EXISTS (SELECT 1 FROM core.course_offering o WHERE o.group_id = g.id)""")
                .query(Integer.class)
                .list()
                .stream()
                .mapToInt(adminTableService::createOfferings)
                .sum();
        int teachers = teacherImportService.importTeachers(programs);
        int keysPending = jdbc.sql("""
                        SELECT count(*) FROM core.offering_v o
                        WHERE o.semester_id IN (%s)
                          AND NOT EXISTS (SELECT 1 FROM anon.offering_key k WHERE k.offering_id = o.offering_id AND k.active)"""
                        .formatted(SYNCED_SEMESTERS))
                .query(Integer.class)
                .single();
        if (keysPending > 0 && keysRunning.compareAndSet(false, true)) {
            Thread.ofVirtual().name("signing-keys").start(this::createKeys);
        }
        return new SyncResult(groups, pastGroups, courses, teachers, keysPending);
    }

    // members of 22C31 (year 3) studied in 22C21 and 22C11 before; those groups point at 22C31 so its members can rate them
    private int createPastGroups() {
        jdbc.sql("""
                        INSERT INTO core.academic_year (during)
                        SELECT daterange((lower(ay.during) - make_interval(years => k))::date,
                                         (lower(ay.during) - make_interval(years => k - 1))::date)
                        FROM core.academic_year ay
                        CROSS JOIN generate_series(1, (SELECT coalesce(max(g.study_year), 1) - 1 FROM core.study_group g
                                                       WHERE g.academic_year_id = ay.id)) k
                        WHERE ay.during @> current_date
                        ON CONFLICT DO NOTHING""")
                .update();
        int created = jdbc.sql("""
                        INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size, successor_id)
                        SELECT g.plan_id, py.id, g.study_year - k,
                               regexp_replace(g.code, '^(.*[A-Z])' || g.study_year || '([0-9]+)$', '\\1' || (g.study_year - k) || '\\2'),
                               g.max_size, g.id
                        FROM core.study_group g
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id AND ay.during @> current_date
                        CROSS JOIN generate_series(1, g.study_year - 1) k
                        JOIN core.academic_year py ON py.during @> ((lower(ay.during) - make_interval(years => k))::date + 7)
                        WHERE g.code ~ ('^.*[A-Z]' || g.study_year || '[0-9]+$')
                        ON CONFLICT (academic_year_id, code) DO NOTHING""")
                .update();
        jdbc.sql("""
                        INSERT INTO core.semester (academic_year_id, no, during, rating_opens_at)
                        SELECT ay.id, s.no,
                               CASE s.no WHEN 1 THEN daterange(lower(ay.during), lower(ay.during) + 140)
                                         ELSE daterange(lower(ay.during) + 147, upper(ay.during)) END,
                               CASE s.no WHEN 1 THEN lower(ay.during) + 140 ELSE upper(ay.during) END
                        FROM core.academic_year ay
                        CROSS JOIN (VALUES (1), (2)) s(no)
                        WHERE upper(ay.during) <= current_date
                          AND EXISTS (SELECT 1 FROM core.study_group g WHERE g.academic_year_id = ay.id AND g.successor_id IS NOT NULL)
                        ON CONFLICT DO NOTHING""")
                .update();
        return created;
    }

    // past teachers come from the faculty's archived PDFs; each file is read once per run of the app
    private int importPastTeachers(List<TimetableProgram> programs) {
        var programOf = new HashMap<String, String>();
        for (var program : programs) {
            String code = program.getCode() == null ? "" : program.getCode().replaceFirst("^an_\\d_", "");
            if (program.getGroups() != null) {
                program.getGroups().forEach(label -> programOf.put(GroupLabel.of(label).group(), code));
            }
        }
        var wanted = jdbc.sql("""
                        SELECT DISTINCT g.academic_year_id, extract(year FROM lower(ay.during))::int AS start_year, g.study_year,
                               s.code AS successor_code
                        FROM core.study_group g
                        JOIN core.study_group s ON s.id = g.successor_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE EXISTS (SELECT 1 FROM core.course_offering o WHERE o.group_id = g.id
                                        AND NOT EXISTS (SELECT 1 FROM core.teaching_assignment ta WHERE ta.offering_id = o.id))""")
                .query()
                .listOfRows();
        var byYear = new LinkedHashMap<Integer, List<TimetableProgram>>();
        for (var row : wanted) {
            String code = programOf.get((String) row.get("successor_code"));
            if (code == null || code.isEmpty()) {
                continue;
            }
            int yearId = ((Number) row.get("academic_year_id")).intValue();
            int startYear = ((Number) row.get("start_year")).intValue();
            int studyYear = ((Number) row.get("study_year")).intValue();
            for (String program : new LinkedHashSet<>(List.of(code, code.split("_")[0]))) {
                for (int semester = 1; semester <= 2; semester++) {
                    String key = startYear + "/" + semester + "/" + studyYear + "/" + program;
                    if (!archivesRead.add(key)) {
                        continue;
                    }
                    try {
                        var archived = timetableService.archived(startYear, semester, studyYear, program);
                        if (archived != null) {
                            byYear.computeIfAbsent(yearId, k -> new ArrayList<>()).add(archived);
                        }
                    } catch (IOException | RuntimeException e) {
                        archivesRead.remove(key);
                        log.warn("Archived timetable {} not read: {}", key, e.toString());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return 0;
                    }
                }
            }
        }
        int changed = 0;
        for (var entry : byYear.entrySet()) {
            createSubgroups(entry.getValue().stream()
                    .flatMap(p -> p.getGroups() == null ? Stream.<String>empty() : p.getGroups().stream())
                    .map(GroupLabel::of)
                    .toList(), entry.getKey());
            changed += teacherImportService.importTeachers(entry.getValue(), entry.getKey());
        }
        return changed;
    }

    private int createGroups(TimetableProgram program) {
        if (program.getGroups() == null || program.getGroups().isEmpty()) {
            return 0;
        }
        var labels = program.getGroups().stream().map(GroupLabel::of).toList();
        int created = jdbc.sql("""
                        INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
                        SELECT pl.id, ay.id, :year, t.group_code, 60
                        FROM core.academic_year ay
                        JOIN core.specialization sp ON sp.name_ro = :specialization
                        JOIN core.faculty f ON f.id = sp.faculty_id AND f.code = 'ACIEE'
                        JOIN core.study_plan pl ON pl.specialization_id = sp.id
                         AND pl.intake @> (extract(year FROM lower(ay.during))::int - :year + 1)
                        CROSS JOIN (SELECT DISTINCT unnest(CAST(:codes AS text[])) AS group_code) t
                        WHERE ay.during @> current_date AND length(t.group_code) <= 16
                        ON CONFLICT (academic_year_id, code) DO NOTHING""")
                .param("year", program.getYear())
                .param("specialization", program.getName())
                .param("codes", labels.stream().map(GroupLabel::group).toArray(String[]::new))
                .update();
        createSubgroups(labels, null);
        return created;
    }

    private void createSubgroups(List<GroupLabel> labels, Integer academicYearId) {
        var split = labels.stream().filter(l -> l.subgroup() != null).toList();
        if (split.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO core.subgroup (group_id, label)
                        SELECT g.id, t.subgroup
                        FROM unnest(CAST(:codes AS text[]), CAST(:subgroups AS text[])) AS t(group_code, subgroup)
                        JOIN core.study_group g ON g.code = t.group_code
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                         AND CASE WHEN CAST(:year AS integer) IS NULL THEN ay.during @> current_date ELSE ay.id = :year END
                        ON CONFLICT (group_id, label) DO NOTHING""")
                .param("codes", split.stream().map(GroupLabel::group).toArray(String[]::new))
                .param("subgroups", split.stream().map(GroupLabel::subgroup).toArray(String[]::new))
                .param("year", academicYearId)
                .update();
    }

    private void createKeys() {
        try {
            for (int semesterId : jdbc.sql(SYNCED_SEMESTERS).query(Integer.class).list()) {
                int created = tokenService.generateKeys(semesterId);
                if (created > 0) {
                    log.info("Created {} signing keys for semester {}", created, semesterId);
                }
            }
        } catch (Exception e) {
            log.warn("Signing keys not created: {}", e.toString());
        } finally {
            keysRunning.set(false);
        }
    }
}
