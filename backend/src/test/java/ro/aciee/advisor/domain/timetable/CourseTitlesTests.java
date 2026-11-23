package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import ro.aciee.advisor.domain.timetable.service.CourseTitles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseTitlesTests {

    private final List<String> titles = List.of("Grafică asistată de calculator", "Limba engleză (anul 1)", "Educație fizică I",
            "Limbaje formale și translatoare I", "Sport de echipă I", "Programarea orientată pe obiecte");

    @Test
    void matchesTimetableSpellingsOfTheSameCourse() {
        assertThat(CourseTitles.match("Grafica asistata pe calculator", titles)).isZero();
        assertThat(CourseTitles.match("Lb. Engleza", titles)).isEqualTo(1);
        assertThat(CourseTitles.match("Educatie Fizica si Sport", titles)).isEqualTo(2);
        assertThat(CourseTitles.match("Limbaje formale și translatoare", titles)).isEqualTo(3);
    }

    @Test
    void leavesUnclearOrUnrelatedNamesUnmatched() {
        assertThat(CourseTitles.match("Sport", titles)).isEqualTo(-1);
        assertThat(CourseTitles.match("Sisteme de operare", titles)).isEqualTo(-1);
        assertThat(CourseTitles.match("Programare", List.of("Programare web", "Programare logică"))).isEqualTo(-1);
    }
}
