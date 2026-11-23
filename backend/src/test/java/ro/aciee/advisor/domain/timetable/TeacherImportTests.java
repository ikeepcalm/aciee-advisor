package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import ro.aciee.advisor.TestData;
import ro.aciee.advisor.TestcontainersConfiguration;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.TeacherImportService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TeacherImportTests {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TeacherImportService importer;

    @Test
    void createsTheTeacherOnceFillsEmptySlotsAndLeavesLeaderChangesAlone() {
        int groupId = TestData.currentGroup(jdbc, "TI21", 30);
        jdbc.sql("INSERT INTO core.subgroup (group_id, label) VALUES (:g, 'A'), (:g, 'B')").param("g", groupId).update();
        int subjectId = TestData.insert(jdbc, "INSERT INTO core.subject (code, name_ro) VALUES ('TI21', 'Programare orientată pe obiecte') RETURNING id");
        int itemId = TestData.insert(jdbc, """
                INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no, lecture_hours, lab_hours, project_hours)
                SELECT plan_id, %d, 3, 1, 2, 2, 1 FROM core.study_group WHERE id = %d RETURNING id""".formatted(subjectId, groupId));
        int offeringId = TestData.insert(jdbc, """
                INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
                SELECT %d, id, plan_id, study_year, 'Programare orientată pe obiecte' FROM core.study_group WHERE id = %d RETURNING id"""
                .formatted(itemId, groupId));
        var program = program(
                lesson(List.of("TI21A", "TI21B"), "C", "Programare orientata pe obiecte", "Conf.dr.ing. Teodor Quintilian"),
                lesson(List.of("TI21A"), "L", "Programare orientată pe obiecte", "T. Quintilian"),
                lesson(List.of("TI21B"), "L", "Programare orientată pe obiecte", "Conf. T.Quintilian"),
                lesson(List.of("TI21A"), "P", "Programare orientată pe obiecte", "Ș.l. Radu Nouvel"),
                lesson(List.of("TI21B"), "P", "Programare orientată pe obiecte", "As. Pavel Vremea"),
                lesson(List.of("TI21A", "TI21B"), "S", "Programare orientată pe obiecte", "M. Altcineva"),
                lesson(List.of("ALT99"), "C", "Programare orientată pe obiecte", "X. Nimeni"));

        assertThat(importer.importTeachers(List.of(program))).isEqualTo(4);
        assertThat(jdbc.sql("""
                        SELECT p.full_name || ' ' || p.title_code || ' ' || p.name_confirmed || ' ' || d.name_ro
                        FROM core.professor p JOIN core.department d ON d.id = p.department_id
                        WHERE p.full_name LIKE '%Quintilian%' OR p.full_name LIKE '%Altcineva%' OR p.full_name LIKE '%Nimeni%'""")
                .query(String.class).list()).containsExactly("Teodor Quintilian conf false Nespecificat");
        assertThat(components(offeringId)).containsExactly("lab - Teodor Quintilian", "lecture - Teodor Quintilian",
                "project A Radu Nouvel", "project B Pavel Vremea");

        assertThat(importer.importTeachers(List.of(program))).isZero();

        int leaderPick = TestData.insert(jdbc, """
                INSERT INTO core.professor (full_name, title_code, department_id, slug)
                SELECT 'Ioana Aleasa', 'asist', department_id, 'ioana-aleasa-ti' FROM core.professor WHERE full_name = 'Teodor Quintilian'
                RETURNING id""");
        jdbc.sql("DELETE FROM core.teaching_assignment WHERE offering_id = :o AND component = 'project'").param("o", offeringId).update();
        jdbc.sql("UPDATE core.teaching_assignment SET professor_id = :p WHERE offering_id = :o AND component = 'lab'")
                .param("p", leaderPick).param("o", offeringId).update();
        jdbc.sql("DELETE FROM core.teaching_assignment WHERE offering_id = :o AND component = 'lecture'").param("o", offeringId).update();
        var changed = program(
                lesson(List.of("TI21A", "TI21B"), "C", "Programare orientată pe obiecte", "Conf.dr.ing. Teodor Quintilian"),
                lesson(List.of("TI21A", "TI21B"), "L", "Programare orientată pe obiecte", "Ș.l. R. Nouvel"),
                lesson(List.of("TI21A"), "P", "Programare orientată pe obiecte", "Ș.l. Radu Nouvel"),
                lesson(List.of("TI21B"), "P", "Programare orientată pe obiecte", "As. Pavel Vremea"));

        assertThat(importer.importTeachers(List.of(changed))).isZero();
        assertThat(components(offeringId)).containsExactly("lab - Ioana Aleasa");
    }

    private List<String> components(int offeringId) {
        return jdbc.sql("""
                        SELECT ta.component || ' ' || coalesce(s.label, '-') || ' ' || p.full_name FROM core.teaching_assignment ta
                        JOIN core.professor p ON p.id = ta.professor_id
                        LEFT JOIN core.subgroup s ON s.id = ta.subgroup_id
                        WHERE ta.offering_id = :o AND ta.valid_during && daterange(current_date, NULL) ORDER BY ta.component, s.label NULLS FIRST""")
                .param("o", offeringId).query(String.class).list();
    }

    private static TimetableProgram program(Lesson... lessons) {
        var program = new TimetableProgram();
        program.setCode("an_3_TI");
        program.setLessons(List.of(lessons));
        return program;
    }

    private static Lesson lesson(List<String> groups, String kind, String subject, String teacher) {
        return new Lesson(1, "08:00", "10:00", WeekParity.ALL, groups, kind, subject, null, teacher, null, List.of());
    }
}
