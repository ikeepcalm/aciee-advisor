package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.aciee.advisor.TestcontainersConfiguration;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableChange;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.TimetableChangeService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TimetableChangeTests {

    @Autowired
    TimetableChangeService changes;

    @Test
    void recordsMovedLabsPerGroupButNotTheFirstSnapshot() {
        String code = "test_" + UUID.randomUUID();
        var lab = new Lesson(3, "12:00", "13:50", WeekParity.ODD, List.of("A1"), "L", "SO", "Y402", "D. Ionescu", null, List.of());
        var lecture = new Lesson(1, "08:00", "09:50", WeekParity.ALL, List.of("A1", "A2"), "C", "Sisteme de operare (SO)", "D02", "prof. X", null, List.of());

        assertThat(changes.record(program(code, lab, lecture))).isZero();
        assertThat(changes.record(program(code, lab, lecture))).isZero();

        var moved = new Lesson(3, "12:00", "13:50", WeekParity.ODD, List.of("A1"), "L", "SO", "Y405", "D. Ionescu", null, List.of());
        assertThat(changes.record(program(code, moved, lecture))).isEqualTo(2);

        assertThat(changes.recent(code, "A1"))
                .extracting(TimetableChange::getChange, TimetableChange::getDay, TimetableChange::getRoom)
                .containsExactlyInAnyOrder(tuple("added", 3, "Y405"), tuple("removed", 3, "Y402"));
        assertThat(changes.recent(code, "A2")).isEmpty();
    }

    @Test
    void ignoresAParseThatLostMostOfTheTimetable() {
        String code = "test_" + UUID.randomUUID();
        var lab = new Lesson(3, "12:00", "13:50", WeekParity.ODD, List.of("A1"), "L", "SO", "Y402", "D. Ionescu", null, List.of());
        var lecture = new Lesson(1, "08:00", "09:50", WeekParity.ALL, List.of("A1", "A2"), "C", "Sisteme de operare (SO)", "D02", "prof. X", null, List.of());
        changes.record(program(code, lab, lecture));

        assertThat(changes.record(program(code))).isZero();
        assertThat(changes.record(program(code, lab, lecture))).isZero();
        assertThat(changes.recent(code, "A1")).isEmpty();
    }

    private static TimetableProgram program(String code, Lesson... lessons) {
        var program = new TimetableProgram();
        program.setCode(code);
        program.setGroups(List.of("A1", "A2"));
        program.setLessons(List.of(lessons));
        return program;
    }
}
