package ro.aciee.advisor.domain.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import ro.aciee.advisor.TestData;
import ro.aciee.advisor.TestcontainersConfiguration;
import ro.aciee.advisor.domain.catalog.model.ProfessorListItem;
import ro.aciee.advisor.domain.catalog.service.CatalogService;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.CalendarService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProfessorProfileTests {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CatalogService catalog;

    @Autowired
    CalendarService calendar;

    @Test
    void statsCountActiveAccountsAndAggregatedRatings() {
        long accounts = jdbc.sql("""
                SELECT count(*) FROM core.user_account a WHERE a.disabled_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM core.email_verification v WHERE v.user_id = a.id)""").query(Long.class).single();

        var stats = catalog.stats();

        assertThat(stats.getStudents()).isEqualTo(accounts);
        assertThat(stats.getRatings()).isNotNegative();
    }

    @Test
    void matchesTheCvProfileWhateverTheNameOrderOrDiacritics() {
        jdbc.sql("""
                INSERT INTO core.professor_profile (name_key, bio_ro, bio_en, photo, cv_url)
                VALUES (core.name_key('Şuşnea Ioan'), 'Profesor.', 'Professor.', '/professors/susnea-ioan.jpg',
                        'https://example.org/Susnea_Ioan.pdf')
                ON CONFLICT (name_key) DO NOTHING""").update();
        TestData.insert(jdbc, """
                INSERT INTO core.department (faculty_id, name_ro)
                SELECT id, 'Calculatoare test' FROM core.faculty WHERE code = 'ACIEE'
                ON CONFLICT (faculty_id, name_ro) DO UPDATE SET name_ro = excluded.name_ro RETURNING id""");
        TestData.insert(jdbc, """
                INSERT INTO core.professor (full_name, title_code, department_id, slug)
                SELECT 'IOAN ȘUȘNEA', 'prof', d.id, 'ioan-susnea-test' FROM core.department d WHERE d.name_ro = 'Calculatoare test'
                RETURNING id""");

        var page = catalog.professor("ioan-susnea-test");

        assertThat(page.getBioRo()).isNotBlank();
        assertThat(page.getPhoto()).isEqualTo("/professors/susnea-ioan.jpg");
        assertThat(page.getCvUrl()).endsWith("Susnea_Ioan.pdf");
        assertThat(page.getOverall()).isNull();
        assertThat(catalog.professors()).filteredOn(item -> item.getSlug().equals("ioan-susnea-test"))
                .extracting(ProfessorListItem::getPhoto, ProfessorListItem::getRatings)
                .containsExactly(tuple("/professors/susnea-ioan.jpg", 0));
    }

    @Test
    void numbersTeachingWeeksFromWeekOneAndSkipsBreaks() {
        int year = TestData.insert(jdbc, "INSERT INTO core.academic_year (during) VALUES ('[2040-10-01,2041-10-01)') RETURNING id");
        TestData.insert(jdbc, """
                INSERT INTO core.semester (academic_year_id, no, during, rating_opens_at, breaks)
                VALUES (%d, 1, '[2040-10-01,2041-02-15)', '2041-01-14 00:00+02', '{[2040-12-22,2041-01-07)}') RETURNING id"""
                .formatted(year));
        var program = new TimetableProgram();
        program.setSemester("ORAR - Semestrul I - Anul universitar 2040-2041");

        var weeks = calendar.weeks(program);

        assertThat(weeks).containsEntry(LocalDate.of(2040, 10, 1), WeekParity.ODD)
                .containsEntry(LocalDate.of(2040, 10, 8), WeekParity.EVEN)
                .containsEntry(LocalDate.of(2040, 12, 17), WeekParity.EVEN)
                .doesNotContainKeys(LocalDate.of(2040, 12, 24), LocalDate.of(2040, 12, 31))
                .containsEntry(LocalDate.of(2041, 1, 7), WeekParity.ODD)
                .hasSize(13);
    }
}
