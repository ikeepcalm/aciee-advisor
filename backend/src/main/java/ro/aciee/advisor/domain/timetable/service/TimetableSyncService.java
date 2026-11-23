package ro.aciee.advisor.domain.timetable.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import ro.aciee.advisor.domain.admin.service.AdminTableService;
import ro.aciee.advisor.domain.timetable.model.SyncResult;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.token.service.TokenService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableSyncService {

    private static final String CURRENT_SEMESTERS = """
            SELECT s.id FROM core.semester s JOIN core.academic_year ay ON ay.id = s.academic_year_id
            WHERE ay.during @> current_date""";

    private final JdbcClient jdbc;
    private final TimetableService timetableService;
    private final AdminTableService adminTableService;
    private final TeacherImportService teacherImportService;
    private final TokenService tokenService;
    private final AtomicBoolean keysRunning = new AtomicBoolean();

    public SyncResult sync() {
        return sync(timetableService.current().getPrograms());
    }

    public synchronized SyncResult sync(List<TimetableProgram> programs) {
        int groups = programs.stream().mapToInt(this::createGroups).sum();
        int courses = jdbc.sql("""
                        SELECT g.id FROM core.study_group g JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE ay.during @> current_date
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
                        .formatted(CURRENT_SEMESTERS))
                .query(Integer.class)
                .single();
        if (keysPending > 0 && keysRunning.compareAndSet(false, true)) {
            Thread.ofVirtual().name("signing-keys").start(this::createKeys);
        }
        return new SyncResult(groups, courses, teachers, keysPending);
    }

    private int createGroups(TimetableProgram program) {
        if (program.getGroups() == null || program.getGroups().isEmpty()) {
            return 0;
        }
        return jdbc.sql("""
                        INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
                        SELECT pl.id, ay.id, :year, t.group_code, 40
                        FROM core.academic_year ay
                        JOIN core.specialization sp ON sp.name_ro = :specialization
                        JOIN core.faculty f ON f.id = sp.faculty_id AND f.code = 'ACIEE'
                        JOIN core.study_plan pl ON pl.specialization_id = sp.id
                         AND pl.intake @> (extract(year FROM lower(ay.during))::int - :year + 1)
                        CROSS JOIN unnest(CAST(:codes AS text[])) AS t(group_code)
                        WHERE ay.during @> current_date AND length(t.group_code) <= 16
                        ON CONFLICT (academic_year_id, code) DO NOTHING""")
                .param("year", program.getYear())
                .param("specialization", program.getName())
                .param("codes", program.getGroups().toArray(String[]::new))
                .update();
    }

    private void createKeys() {
        try {
            for (int semesterId : jdbc.sql(CURRENT_SEMESTERS).query(Integer.class).list()) {
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
