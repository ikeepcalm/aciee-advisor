package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.TimetableParser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TimetableParserTests {

    @Test
    void readsMergedCellsAndAlternatingWeeks() throws Exception {
        byte[] pdf;
        try (var in = getClass().getResourceAsStream("/timetable/an_2_CTI.pdf")) {
            pdf = in.readAllBytes();
        }
        var program = TimetableParser.parse(pdf);

        assertThat(program.getTitle()).isEqualTo("ANUL II - CALCULATOARE ȘI TEHNOLOGIA INFORMAȚIEI");
        assertThat(program.getSemester()).contains("Semestrul I");
        assertThat(program.getGroups()).containsExactly("22C21A", "22C21B", "22C22A");
        assertThat(program.getLessons()).hasSize(38);
        assertThat(program.getLessons())
                .filteredOn(l -> l.getDay() == 1 && l.getStart().equals("12:00"))
                .extracting(Lesson::getWeeks, Lesson::getGroups, Lesson::getKind, Lesson::getSubject)
                .containsExactly(
                        tuple(WeekParity.ALL, List.of("22C21A"), "L", "SO"),
                        tuple(WeekParity.ALL, List.of("22C21B"), "L", "POO"),
                        tuple(WeekParity.ODD, List.of("22C22A"), "L", "TS"),
                        tuple(WeekParity.EVEN, List.of("22C22A"), null, "Ed. fizică II"));
        assertThat(program.getLessons())
                .filteredOn(l -> l.getDay() == 2 && l.getStart().equals("08:00") && l.getWeeks() == WeekParity.ODD)
                .extracting(Lesson::getGroups)
                .contains(List.of("22C21A", "22C21B"));
    }

    @Test
    void splitsCellsIntoSubjectRoomAndTeacher() throws Exception {
        byte[] pdf;
        try (var in = getClass().getResourceAsStream("/timetable/an_2_CTI.pdf")) {
            pdf = in.readAllBytes();
        }
        var lessons = TimetableParser.parse(pdf).getLessons();

        assertThat(lessons)
                .filteredOn(l -> l.getDay() == 1 && l.getStart().equals("12:00") && l.getGroups().equals(List.of("22C21A")))
                .extracting(Lesson::getSubject, Lesson::getRoom, Lesson::getTeacher, Lesson::getNote)
                .containsExactly(tuple("SO", "G308", "D.Voipan", null));
        assertThat(lessons)
                .extracting(Lesson::getSubject, Lesson::getRoom, Lesson::getTeacher, Lesson::getNote)
                .contains(
                        tuple("Programarea orientată pe obiecte (POO)", "AN101", "prof. A.Cocu", "S1-7"),
                        tuple("Limba engleză", "AN113", "I. Cighir", null),
                        tuple("Ed. fizică II", null, "A.Berdilă", "Stadionul Portu Roșu"));
        assertThat(lessons)
                .filteredOn(l -> l.getSubject().startsWith("Sisteme de operare (SO) S1-7 / POO"))
                .allSatisfy(l -> assertThat(l.getNote()).contains("Y105 prof. E.Pecheanu", "G306, G308 prof. A.Cocu"));
        assertThat(lessons).allSatisfy(l -> assertThat(l.getLines()).isNotEmpty());
    }
}
