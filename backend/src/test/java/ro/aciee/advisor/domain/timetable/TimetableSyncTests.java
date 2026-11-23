package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import ro.aciee.advisor.TestData;
import ro.aciee.advisor.TestcontainersConfiguration;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.service.TimetableSyncService;
import ro.aciee.advisor.domain.token.model.RateableOffering;
import ro.aciee.advisor.domain.token.service.TokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TimetableSyncTests {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TimetableSyncService sync;

    @Autowired
    TokenService tokens;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void createsThisYearsGroupsWithSubgroupsOnTheirPlanWithTheirCoursesOnce() {
        TestData.currentGroup(jdbc, "SYX", 1);
        var program = new TimetableProgram();
        program.setCode("an_2_CTI");
        program.setYear(2);
        program.setName("Calculatoare");
        program.setGroups(List.of("SY21A", "SY21B"));
        program.setLessons(List.of());
        var unknown = new TimetableProgram();
        unknown.setCode("an_2_IETTI");
        unknown.setYear(2);
        unknown.setName("Electronică și telecomunicații");
        unknown.setGroups(List.of("SY29Z"));
        unknown.setLessons(List.of());

        var first = sync.sync(List.of(program, unknown));

        assertThat(first.groups()).isEqualTo(1);
        assertThat(first.courses()).isPositive();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM core.study_group g
                        JOIN core.study_plan pl ON pl.id = g.plan_id
                        JOIN core.specialization sp ON sp.id = pl.specialization_id
                        JOIN core.faculty f ON f.id = sp.faculty_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE g.code = 'SY21' AND ay.during @> current_date AND g.study_year = 2
                          AND f.code = 'ACIEE' AND sp.name_ro = 'Calculatoare'
                          AND pl.intake @> (extract(year FROM lower(ay.during))::int - 1)
                          AND EXISTS (SELECT 1 FROM core.course_offering o WHERE o.group_id = g.id)""")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT string_agg(s.label, ',' ORDER BY s.label) FROM core.subgroup s
                        JOIN core.study_group g ON g.id = s.group_id WHERE g.code = 'SY21'""").query(String.class).single())
                .isEqualTo("A,B");
        assertThat(jdbc.sql("SELECT count(*) FROM core.study_group WHERE code IN ('SY29Z', 'SY29')").query(Long.class).single()).isZero();

        assertThat(first.pastGroups()).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT p.study_year || ' ' || (upper(pay.during) = lower(ay.during))
                               || ' ' || (SELECT count(*) FROM core.semester s WHERE s.academic_year_id = pay.id AND s.rating_opens_at <= now())
                               || ' ' || (SELECT count(*) > 0 FROM core.course_offering o WHERE o.group_id = p.id)
                        FROM core.study_group p
                        JOIN core.study_group g ON g.id = p.successor_id
                        JOIN core.academic_year pay ON pay.id = p.academic_year_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE p.code = 'SY11' AND g.code = 'SY21'""").query(String.class).single())
                .isEqualTo("1 true 2 true");

        int student = TestData.account(jdbc, "past.sync");
        int admin = TestData.account(jdbc, "past.admin");
        jdbc.sql("INSERT INTO core.user_role (user_id, role_code) VALUES (:id, 'admin')").param("id", admin).update();
        transactions.executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.user_id', :admin, true)").param("admin", String.valueOf(admin)).query(String.class).single();
            jdbc.sql("""
                        INSERT INTO core.group_membership (user_id, group_id, kind, source, valid_during)
                        SELECT CAST(:user AS integer), g.id, 'regular', 'university_sync', ay.during
                        FROM core.study_group g JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE g.code = 'SY21' AND ay.during @> current_date""")
                .param("user", student)
                .update();
        });
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(student, null, List.of()));
        try {
            var years = tokens.offerings().stream().map(RateableOffering::getAcademicYear).distinct().count();
            assertThat(years).isEqualTo(2);
        } finally {
            SecurityContextHolder.clearContext();
        }

        var again = sync.sync(List.of(program, unknown));

        assertThat(again.groups()).isZero();
        assertThat(again.pastGroups()).isZero();
        assertThat(again.courses()).isZero();
    }
}
