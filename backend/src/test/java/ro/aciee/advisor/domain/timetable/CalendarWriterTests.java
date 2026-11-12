package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.CalendarWriter;
import ro.aciee.advisor.domain.timetable.service.LessonNames;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarWriterTests {

    private static final LocalDate ODD_MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(2027, 1, 11);

    private final Lesson lecture = lesson(1, "08:00", "09:50", WeekParity.ALL, List.of("22C11A", "22C11B"), "C",
            "Analiza Matematica (AM)", "D02", "prof. I. Lixandru");
    private final Lesson oddLab = lesson(3, "12:00", "13:50", WeekParity.ODD, List.of("22C11A"), "L", "AM", "Y102", "I. Lixandru");
    private final Lesson evenLab = lesson(3, "12:00", "13:50", WeekParity.EVEN, List.of("22C11A"), "L", "AM", "Y102", "I. Lixandru");
    private final Lesson otherGroup = lesson(2, "10:00", "11:50", WeekParity.ALL, List.of("22C12A"), "S", "ALGAD", "Y102", "A. Ghiura");

    @Test
    void placesOddAndEvenWeeksFromTheCalibratedMonday() {
        assertThat(CalendarWriter.firstOccurrence(lecture, ODD_MONDAY, FIRST_DAY)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(CalendarWriter.firstOccurrence(oddLab, ODD_MONDAY, FIRST_DAY)).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(CalendarWriter.firstOccurrence(evenLab, ODD_MONDAY, FIRST_DAY)).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void writesOnlyTheGroupsLessonsWithLinksAndTheRatingReminder() {
        var program = program(lecture, oddLab, otherGroup);
        lecture.setCourseId(7);
        oddLab.setCourseId(7);
        String ics = CalendarWriter.write(program, "22C11A", ODD_MONDAY, FIRST_DAY, LAST_DAY,
                LocalDate.of(2027, 1, 12), "https://acieeadvisor.ro");
        String unfolded = ics.replace("\r\n ", "");

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(unfolded).contains("SUMMARY:Analiza Matematica (Curs)", "SUMMARY:Analiza Matematica (Laborator)");
        assertThat(unfolded).doesNotContain("ALGAD");
        assertThat(unfolded).contains("DTSTART;TZID=Europe/Bucharest:20261014T120000", "RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20270111T215959Z");
        assertThat(unfolded).contains("DESCRIPTION:prof. I. Lixandru\\nCe spun colegii despre curs: https://acieeadvisor.ro/courses/7");
        assertThat(unfolded).contains("DTSTART;VALUE=DATE:20270112", "SUMMARY:Evaluează cursurile semestrului (~2 min)");
        assertThat(Arrays.stream(ics.split("\r\n"))).allMatch(line -> line.getBytes(StandardCharsets.UTF_8).length <= 75);
    }

    @Test
    void escapesCalendarText() {
        var tricky = lesson(1, "08:00", "09:50", WeekParity.ALL, List.of("G"), "C", "Rețele, protocoale; teorie (RPT)",
                "A\\1", null);

        String ics = CalendarWriter.write(program(tricky), "G", ODD_MONDAY, FIRST_DAY, LAST_DAY, null, "https://x");

        assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:Rețele\\, protocoale\\; teorie (Curs)", "LOCATION:A\\\\1");
    }

    @Test
    void resolvesAbbreviationsToTheLectureName() {
        program(lecture, oddLab, otherGroup);

        assertThat(oddLab.getCourse()).isEqualTo("Analiza Matematica");
        assertThat(lecture.getCourse()).isEqualTo("Analiza Matematica");
        assertThat(otherGroup.getCourse()).isEqualTo("ALGAD");
        assertThat(LessonNames.fold("Analiza Matematică")).isEqualTo(LessonNames.fold(oddLab.getCourse()));
    }

    private static TimetableProgram program(Lesson... lessons) {
        var program = new TimetableProgram();
        program.setCode("an_1_CTI");
        program.setYear(1);
        program.setName("Calculatoare");
        program.setSemester("ORAR - Semestrul I - Anul universitar 2026-2027");
        program.setUpdatedAt(Instant.parse("2026-10-01T08:00:00Z"));
        program.setGroups(List.of("22C11A", "22C11B", "22C12A", "G"));
        program.setLessons(List.of(lessons));
        LessonNames.attachCourseNames(program);
        return program;
    }

    private static Lesson lesson(int day, String start, String end, WeekParity weeks, List<String> groups, String kind,
                                 String subject, String room, String teacher) {
        return new Lesson(day, start, end, weeks, groups, kind, subject, room, teacher, null, List.of());
    }
}
