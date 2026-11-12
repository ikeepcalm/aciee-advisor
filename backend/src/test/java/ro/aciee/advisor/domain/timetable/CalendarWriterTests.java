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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarWriterTests {

    private static final LocalDate RATING_OPENS = LocalDate.of(2027, 1, 12);
    private static final Map<LocalDate, WeekParity> WEEKS = teachingWeeks();

    private final Lesson lecture = lesson(1, "08:00", "09:50", WeekParity.ALL, List.of("22C11A", "22C11B"), "C",
            "Analiza Matematica (AM)", "D02", "prof. I. Lixandru");
    private final Lesson oddLab = lesson(3, "12:00", "13:50", WeekParity.ODD, List.of("22C11A"), "L", "AM", "Y102", "I. Lixandru");
    private final Lesson evenLab = lesson(3, "12:00", "13:50", WeekParity.EVEN, List.of("22C11A"), "L", "AM", "Y102", "I. Lixandru");
    private final Lesson otherGroup = lesson(2, "10:00", "11:50", WeekParity.ALL, List.of("22C12A"), "S", "ALGAD", "Y102", "A. Ghiura");

    @Test
    void placesOddAndEvenWeeksAndSkipsTheBreak() {
        assertThat(CalendarWriter.occurrences(lecture, WEEKS)).hasSize(14).startsWith(LocalDate.of(2026, 9, 28));
        assertThat(CalendarWriter.occurrences(oddLab, WEEKS)).startsWith(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 14))
                .endsWith(LocalDate.of(2026, 12, 9), LocalDate.of(2027, 1, 6));
        assertThat(CalendarWriter.occurrences(evenLab, WEEKS)).startsWith(LocalDate.of(2026, 10, 7))
                .endsWith(LocalDate.of(2026, 12, 16), LocalDate.of(2027, 1, 13));
    }

    @Test
    void writesOnlyTheGroupsLessonsWithLinksAndTheRatingReminder() {
        var program = program(lecture, oddLab, otherGroup);
        lecture.setCourseId(7);
        oddLab.setCourseId(7);
        String ics = CalendarWriter.write(program, "22C11A", WEEKS, RATING_OPENS, "https://acieeadvisor.ro");
        String unfolded = ics.replace("\r\n ", "");

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(unfolded).contains("SUMMARY:Analiza Matematica (Curs)", "SUMMARY:Analiza Matematica (Laborator)");
        assertThat(unfolded).doesNotContain("ALGAD");
        assertThat(unfolded).contains("DTSTART;TZID=Europe/Bucharest:20260930T120000", "RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=6",
                "DTSTART;TZID=Europe/Bucharest:20270106T120000", "RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=12",
                "DTSTART;TZID=Europe/Bucharest:20270104T080000", "RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=2");
        assertThat(unfolded).contains("DESCRIPTION:prof. I. Lixandru\\nCe spun colegii despre curs: https://acieeadvisor.ro/courses/7");
        assertThat(unfolded).contains("DTSTART;VALUE=DATE:20270112", "SUMMARY:Evaluează cursurile semestrului (~2 min)");
        assertThat(Arrays.stream(ics.split("\r\n"))).allMatch(line -> line.getBytes(StandardCharsets.UTF_8).length <= 75);
    }

    @Test
    void escapesCalendarText() {
        var tricky = lesson(1, "08:00", "09:50", WeekParity.ALL, List.of("G"), "C", "Rețele, protocoale; teorie (RPT)",
                "A\\1", null);

        String ics = CalendarWriter.write(program(tricky), "G", WEEKS, null, "https://x");

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

    private static Map<LocalDate, WeekParity> teachingWeeks() {
        var weeks = new LinkedHashMap<LocalDate, WeekParity>();
        for (LocalDate monday = LocalDate.of(2026, 9, 28); monday.isBefore(RATING_OPENS); monday = monday.plusWeeks(1)) {
            if (monday.getMonthValue() != 12 || monday.getDayOfMonth() < 21) {
                weeks.put(monday, weeks.size() % 2 == 0 ? WeekParity.ODD : WeekParity.EVEN);
            }
        }
        return weeks;
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
