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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TimetableSyncTests {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TimetableSyncService sync;

    @Test
    void createsThisYearsGroupsOnTheirPlanWithTheirCoursesOnce() {
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

        assertThat(first.groups()).isEqualTo(2);
        assertThat(first.courses()).isPositive();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM core.study_group g
                        JOIN core.study_plan pl ON pl.id = g.plan_id
                        JOIN core.specialization sp ON sp.id = pl.specialization_id
                        JOIN core.faculty f ON f.id = sp.faculty_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE g.code IN ('SY21A', 'SY21B') AND ay.during @> current_date AND g.study_year = 2
                          AND f.code = 'ACIEE' AND sp.name_ro = 'Calculatoare'
                          AND pl.intake @> (extract(year FROM lower(ay.during))::int - 1)
                          AND EXISTS (SELECT 1 FROM core.course_offering o WHERE o.group_id = g.id)""")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM core.study_group WHERE code = 'SY29Z'").query(Long.class).single()).isZero();

        var again = sync.sync(List.of(program, unknown));

        assertThat(again.groups()).isZero();
        assertThat(again.courses()).isZero();
    }
}
