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
        String group = "TI21A";
        int groupId = TestData.currentGroup(jdbc, group, 30);
        int subjectId = TestData.insert(jdbc, "INSERT INTO core.subject (code, name_ro) VALUES ('TI21', 'Programare orientată pe obiecte') RETURNING id");
        int itemId = TestData.insert(jdbc, """
                INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no, lecture_hours, lab_hours)
                SELECT plan_id, %d, 3, 1, 2, 2 FROM core.study_group WHERE id = %d RETURNING id""".formatted(subjectId, groupId));
        int offeringId = TestData.insert(jdbc, """
                INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
                SELECT %d, id, plan_id, study_year, 'Programare orientată pe obiecte' FROM core.study_group WHERE id = %d RETURNING id"""
                .formatted(itemId, groupId));
        var program = program(
                lesson(group, "C", "Programare orientata pe obiecte", "Conf.dr.ing. Teodor Quintilian"),
                lesson(group, "L", "Programare orientată pe obiecte", "T. Quintilian"),
                lesson(group, "S", "Programare orientată pe obiecte", "M. Altcineva"),
                lesson("ALT99", "C", "Programare orientată pe obiecte", "X. Nimeni"));

        assertThat(importer.importTeachers(List.of(program))).isEqualTo(2);
        assertThat(jdbc.sql("""
                        SELECT p.full_name || ' ' || p.title_code || ' ' || p.name_confirmed || ' ' || d.name_ro
                        FROM core.professor p JOIN core.department d ON d.id = p.department_id
                        WHERE p.full_name LIKE '%Quintilian%' OR p.full_name LIKE '%Altcineva%' OR p.full_name LIKE '%Nimeni%'""")
                .query(String.class).list()).containsExactly("Teodor Quintilian conf false Nespecificat");
        assertThat(components(offeringId)).containsExactly("lab Teodor Quintilian", "lecture Teodor Quintilian");

        assertThat(importer.importTeachers(List.of(program))).isZero();

        int leaderPick = TestData.insert(jdbc, """
                INSERT INTO core.professor (full_name, title_code, department_id, slug)
                SELECT 'Ioana Aleasa', 'asist', department_id, 'ioana-aleasa-ti' FROM core.professor WHERE full_name = 'Teodor Quintilian'
                RETURNING id""");
        jdbc.sql("UPDATE core.teaching_assignment SET professor_id = :p WHERE offering_id = :o AND component = 'lab'")
                .param("p", leaderPick).param("o", offeringId).update();
        jdbc.sql("DELETE FROM core.teaching_assignment WHERE offering_id = :o AND component = 'lecture'").param("o", offeringId).update();
        var changed = program(
                lesson(group, "C", "Programare orientată pe obiecte", "Conf.dr.ing. Teodor Quintilian"),
                lesson(group, "L", "Programare orientată pe obiecte", "Ș.l. R. Nouvel"));

        assertThat(importer.importTeachers(List.of(changed))).isZero();
        assertThat(components(offeringId)).containsExactly("lab Ioana Aleasa");
    }

    private List<String> components(int offeringId) {
        return jdbc.sql("""
                        SELECT ta.component || ' ' || p.full_name FROM core.teaching_assignment ta
                        JOIN core.professor p ON p.id = ta.professor_id
                        WHERE ta.offering_id = :o AND ta.valid_during && daterange(current_date, NULL) ORDER BY ta.component""")
                .param("o", offeringId).query(String.class).list();
    }

    private static TimetableProgram program(Lesson... lessons) {
        var program = new TimetableProgram();
        program.setCode("an_3_TI");
        program.setLessons(List.of(lessons));
        return program;
    }

    private static Lesson lesson(String group, String kind, String subject, String teacher) {
        return new Lesson(1, "08:00", "10:00", WeekParity.ALL, List.of(group), kind, subject, null, teacher, null, List.of());
    }
}
