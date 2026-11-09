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
        assertThat(program.getGroups()).containsExactly("22C21a", "22C21b", "22C22a");
        assertThat(program.getLessons()).hasSize(38);
        assertThat(program.getLessons())
                .filteredOn(l -> l.getDay() == 1 && l.getStart().equals("12:00"))
                .extracting(Lesson::getWeeks, Lesson::getGroups, Lesson::getKind, Lesson::getTitle)
                .containsExactly(
                        tuple(WeekParity.ALL, List.of("22C21a"), "L", "SO G308"),
                        tuple(WeekParity.ALL, List.of("22C21b"), "L", "POO Y505"),
                        tuple(WeekParity.ODD, List.of("22C22a"), "L", "TS Y402"),
                        tuple(WeekParity.EVEN, List.of("22C22a"), null, "Ed. fizică II Stadionul Portu Roșu"));
        assertThat(program.getLessons())
                .filteredOn(l -> l.getDay() == 2 && l.getStart().equals("08:00") && l.getWeeks() == WeekParity.ODD)
                .extracting(Lesson::getGroups)
                .contains(List.of("22C21a", "22C21b"));
    }
}
