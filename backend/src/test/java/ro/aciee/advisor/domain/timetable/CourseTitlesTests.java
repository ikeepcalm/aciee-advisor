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
    void matchesAbbreviationsAndPicksTheRightPartOfANumberedCourse() {
        var numbered = List.of("Programarea calculatoarelor și limbaje de programare I",
                "Programarea calculatoarelor și limbaje de programare II", "Informatică aplicată I", "Informatică aplicată II",
                "Calculatoare numerice I", "Calculatoare numerice II", "Teoria probabilităților și statistică matematică",
                "Limba engleză (anul 1)");
        assertThat(CourseTitles.match("Calculatoare numerice I", numbered)).isEqualTo(4);
        assertThat(CourseTitles.match("Calculatoare numerice II", numbered)).isEqualTo(5);
        assertThat(CourseTitles.match("Programarea Calculatoarelor si Limbaje de Programare I", numbered)).isZero();
        assertThat(CourseTitles.match("PCLP II", numbered)).isEqualTo(1);
        assertThat(CourseTitles.match("IA2", numbered)).isEqualTo(3);
        assertThat(CourseTitles.match("TPSM", numbered)).isEqualTo(6);
        assertThat(CourseTitles.match("Engleză", numbered)).isEqualTo(7);
        assertThat(CourseTitles.match("POO", titles)).isEqualTo(5);
    }

    @Test
    void leavesUnclearOrUnrelatedNamesUnmatched() {
        assertThat(CourseTitles.match("Sport", titles)).isEqualTo(-1);
        assertThat(CourseTitles.match("Sisteme de operare", titles)).isEqualTo(-1);
        assertThat(CourseTitles.match("Programare", List.of("Programare web", "Programare logică"))).isEqualTo(-1);
    }
}
