package ro.aciee.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"spring.datasource.username=test", "spring.datasource.password=test"})
@Transactional
@Import(TestcontainersConfiguration.class)
class SchemaTests {

    @Autowired
    JdbcClient jdbc;

    int planId;
    int groupId;
    int offeringId;
    int assignmentId;

    @BeforeEach
    void seedCurriculum() {
        int facultyId = insert("INSERT INTO core.faculty (code, name_ro) VALUES ('T', 'Test') RETURNING id");
        int departmentId = insert("INSERT INTO core.department (faculty_id, name_ro) VALUES (%d, 'Calculatoare') RETURNING id".formatted(facultyId));
        int specializationId = insert("""
                INSERT INTO core.specialization (faculty_id, code, name_ro, degree, duration_years)
                VALUES (%d, 'T', 'Test', 'BSc', 4) RETURNING id""".formatted(facultyId));
        int yearId = insert("INSERT INTO core.academic_year (during) VALUES ('[2025-10-01,2026-10-01)') RETURNING id");
        insert("""
                INSERT INTO core.semester (academic_year_id, no, during, rating_opens_at)
                VALUES (%d, 1, '[2025-10-01,2026-02-15)', '2026-02-15') RETURNING id""".formatted(yearId));
        planId = insert("INSERT INTO core.study_plan (specialization_id, intake) VALUES (%d, '[2020,2030)') RETURNING id".formatted(specializationId));
        int subjectId = insert("INSERT INTO core.subject (code, name_ro) VALUES ('BD', 'Baze de date') RETURNING id");
        int itemId = insert("""
                INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no)
                VALUES (%d, %d, 3, 1) RETURNING id""".formatted(planId, subjectId));
        groupId = insert("""
                INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
                VALUES (%d, %d, 3, '3311', 30) RETURNING id""".formatted(planId, yearId));
        offeringId = insert("""
                INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
                VALUES (%d, %d, %d, 3, 'Baze de date') RETURNING id""".formatted(itemId, groupId, planId));
        int professorId = insert("""
                INSERT INTO core.professor (full_name, title_code, department_id, slug)
                VALUES ('Ion Popescu', 'conf', %d, 'ion-popescu') RETURNING id""".formatted(departmentId));
        assignmentId = insert("""
                INSERT INTO core.teaching_assignment (offering_id, group_id, professor_id, component, valid_during)
                VALUES (%d, %d, %d, 'lecture', '[2025-10-01,2026-02-15)') RETURNING id""".formatted(offeringId, groupId, professorId));
    }

    @Test
    void curriculumSeedHoldsEveryCalculatoarePlan() {
        var items = jdbc.sql("""
                        SELECT lower(p.intake) AS intake, count(*) AS items
                        FROM core.curriculum_item ci
                        JOIN core.study_plan p ON p.id = ci.plan_id
                        JOIN core.specialization s ON s.id = p.specialization_id
                        WHERE s.code = 'C'
                        GROUP BY p.intake ORDER BY p.intake""")
                .query().listOfRows();
        assertThat(items).extracting(row -> row.get("intake")).containsExactly(2023, 2024, 2025);
        assertThat(jdbc.sql("""
                        SELECT array_agg(sub.name_ro ORDER BY sub.name_ro)::text
                        FROM core.curriculum_item ci
                        JOIN core.subject sub ON sub.id = ci.subject_id
                        JOIN core.study_plan p ON p.id = ci.plan_id
                        WHERE lower(p.intake) = 2025 AND ci.study_year = 4 AND ci.semester_no = 2 AND ci.elective_slot = 3""")
                .query(String.class).single()).isEqualTo("{E-commerce,\"Regăsirea informației\"}");
    }

    @Test
    void studyYearCannotExceedDuration() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE core.study_group SET study_year = 5 WHERE id = :id").param("id", groupId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("exceeds the specialization duration");
    }

    @Test
    void auditTriggerRecordsEachStatement() {
        jdbc.sql("UPDATE core.study_group SET max_size = 25 WHERE id = :id").param("id", groupId).update();
        Map<String, Object> row = jdbc.sql("""
                        SELECT op, old ->> 'max_size' AS before, new ->> 'max_size' AS after
                        FROM core.audit_log WHERE table_name = 'study_group' AND row_id = :id AND op = 'UPDATE'""")
                .param("id", groupId)
                .query().singleRow();
        assertThat(row).containsEntry("before", "30").containsEntry("after", "25");
    }

    @Test
    void auditPairsRowsByTheWholePrimaryKey() {
        int subjectId = insert("INSERT INTO core.subject (code, name_ro) VALUES ('SO', 'Sisteme de operare') RETURNING id");
        int itemId = insert("""
                INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no)
                VALUES (%d, %d, 3, 1) RETURNING id""".formatted(planId, subjectId));
        int secondOfferingId = insert("""
                INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
                VALUES (%d, %d, %d, 3, 'Sisteme de operare') RETURNING id""".formatted(itemId, groupId, planId));
        int userId = insert("INSERT INTO core.user_account (email, password_hash) VALUES ('ion@student.ugal.ro', 'x') RETURNING id");
        for (int offering : List.of(offeringId, secondOfferingId)) {
            int requestId = insert("""
                    INSERT INTO core.membership_request (user_id, group_id, offering_id, kind, status, decided_at)
                    VALUES (%d, %d, %d, 'repeat', 'approved', now()) RETURNING id""".formatted(userId, groupId, offering));
            jdbc.sql("INSERT INTO core.offering_enrolment (user_id, offering_id, request_id) VALUES (%d, %d, %d)"
                    .formatted(userId, offering, requestId)).update();
        }

        jdbc.sql("UPDATE core.offering_enrolment SET request_id = request_id WHERE user_id = :id").param("id", userId).update();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM core.audit_log
                        WHERE table_name = 'offering_enrolment' AND row_id = :id AND op = 'UPDATE'
                          AND old ->> 'offering_id' = new ->> 'offering_id'""")
                .param("id", userId).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void studentHasOneHomeGroupAtATime() {
        int userId = insert("INSERT INTO core.user_account (email, password_hash) VALUES ('ana@student.ugal.ro', 'x') RETURNING id");
        String membership = """
                INSERT INTO core.group_membership (user_id, group_id, kind, source, valid_during)
                VALUES (%d, %d, 'regular', 'university_sync', '%s')""";
        jdbc.sql(membership.formatted(userId, groupId, "[2025-10-01,2026-03-01)")).update();
        jdbc.sql(membership.formatted(userId, groupId, "[2026-03-01,2026-10-01)")).update();
        assertThatThrownBy(() -> jdbc.sql(membership.formatted(userId, groupId, "[2026-01-01,2026-02-01)")).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replacementCycleDoesNotHangRootResolution() {
        int a = insert("INSERT INTO core.subject (code, name_ro) VALUES ('A', 'A') RETURNING id");
        int b = insert("INSERT INTO core.subject (code, name_ro, replaced_by_id) VALUES ('B', 'B', %d) RETURNING id".formatted(a));
        int c = insert("INSERT INTO core.subject (code, name_ro, replaced_by_id) VALUES ('C', 'C', %d) RETURNING id".formatted(b));
        assertThat(jdbc.sql("SELECT root_id FROM core.subject_root_v WHERE subject_id = :c").param("c", c).query(Integer.class).single())
                .isEqualTo(a);

        jdbc.sql("UPDATE core.subject SET replaced_by_id = :c WHERE id = :a").param("c", c).param("a", a).update();
        assertThat(jdbc.sql("SELECT count(*) FROM core.subject_root_v WHERE subject_id IN (:ids)")
                .param("ids", List.of(a, b, c)).query(Long.class).single()).isZero();
    }

    @Test
    void ratingNeedsAtLeastOneAxis() {
        byte[] keyHash = submission();
        assertThatThrownBy(() -> rate(keyHash, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void radarStaysLockedBelowK() {
        for (int i = 0; i < 4; i++) {
            rate(submission(), 8);
        }
        refresh();
        assertThat(radar()).containsEntry("n", 4L).containsEntry("a01", null);

        rate(submission(), 3);
        refresh();
        assertThat(radar()).containsEntry("n", 5L);
        assertThat(radar().get("a01").toString()).isEqualTo("7.0");
    }

    private Map<String, Object> radar() {
        return jdbc.sql("SELECT n, a01 FROM anon.prof_subject_radar").query().singleRow();
    }

    private void refresh() {
        jdbc.sql("SELECT anon.refresh_aggregates()").query().listOfRows();
    }

    private byte[] submission() {
        byte[] fingerprint = new byte[32];
        jdbc.sql("""
                        INSERT INTO anon.offering_key (fingerprint, offering_id, public_key) VALUES (:fp, :offering, '\\x00')
                        ON CONFLICT DO NOTHING""")
                .param("fp", fingerprint).param("offering", offeringId).update();
        byte[] keyHash = new byte[32];
        new SecureRandom().nextBytes(keyHash);
        jdbc.sql("INSERT INTO anon.submission (key_hash, offering_id, key_fingerprint) VALUES (:hash, :offering, :fp)")
                .param("hash", keyHash).param("offering", offeringId).param("fp", fingerprint).update();
        return keyHash;
    }

    private void rate(byte[] keyHash, Integer a01) {
        jdbc.sql("""
                        INSERT INTO anon.rating (key_hash, teaching_assignment_id, offering_id, a01)
                        VALUES (:hash, :assignment, :offering, :a01)""")
                .param("hash", keyHash).param("assignment", assignmentId).param("offering", offeringId).param("a01", a01, Types.SMALLINT)
                .update();
    }

    private int insert(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
