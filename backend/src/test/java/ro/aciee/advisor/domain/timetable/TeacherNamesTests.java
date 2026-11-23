package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import ro.aciee.advisor.domain.timetable.service.TeacherNames;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherNamesTests {

    @Test
    void groupsSpellingsOrderAndTyposOfOneTeacher() {
        var clusters = TeacherNames.cluster(List.of(
                "Ș.l.dr.ing. B. Dumitrașcu", "S.l. dr. ing. Dumitrascu B.", "Sl .dr. ing. Bogdan Dumitrascu",
                "S.l. Dumitrascu Bogdan", "Ș.l.dr.ing. Bogdan Dumitrașcu",
                "G. Szabo", "G.Szabo", "G. Shabo",
                "Conf.Dr.Ing Anisia Culea Florescu", "Conf.dr.ing. Culea Florescu Anisia Luiza", "M. Culea",
                "As.drd.ing. George Andrei Marin", "G. Marin", "prof. G. Mocanu", "Prof Mocanu George",
                "Ș.l.dr.ing. Mihaela Andrei", "Ș.l.dr.ing. M. Andrei",
                "S.l. dr. ing. Balanuta", "D. Balanuta", "S.l.dr.ing Ciprian Daniel Balanuta"));

        assertThat(clusters).extracting(TeacherNames.Cluster::displayName).containsExactlyInAnyOrder(
                "Bogdan Dumitrașcu", "G. Szabo", "Culea Florescu Anisia Luiza", "M. Culea", "George Andrei Marin",
                "Mocanu George", "Mihaela Andrei", "Ciprian Daniel Balanuta");
    }

    @Test
    void takesTheTitleOnlyFromAPrefixThatMeansOne() {
        var clusters = TeacherNames.cluster(List.of("prof. A.Cocu", "S.l. E. Răducan", "Răducan E.", "Prof.dr.ing. Dorel Aiordăchioaie",
                "As.Lil. Scintei", "conf. R.Șolea"));

        assertThat(clusters).extracting(TeacherNames.Cluster::displayName, TeacherNames.Cluster::title).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("A. Cocu", "cadru"),
                org.assertj.core.groups.Tuple.tuple("E. Răducan", "sl"),
                org.assertj.core.groups.Tuple.tuple("Dorel Aiordăchioaie", "prof"),
                org.assertj.core.groups.Tuple.tuple("L. Scintei", "asist"),
                org.assertj.core.groups.Tuple.tuple("R. Șolea", "conf"));
    }

    @Test
    void matchesAnExistingProfessorByFullNameOrInitial() {
        var cluster = TeacherNames.cluster(List.of("prof. I. Șușnea")).getFirst();

        assertThat(cluster.matches("Ioan Șușnea")).isTrue();
        assertThat(cluster.matches("ȘUȘNEA IOAN")).isTrue();
        assertThat(cluster.matches("Mihai Șușnea")).isFalse();
    }
}
