package ro.aciee.advisor.domain.preview;

import org.junit.jupiter.api.Test;
import ro.aciee.advisor.domain.catalog.model.CoursePage;
import ro.aciee.advisor.domain.catalog.model.Lookups;
import ro.aciee.advisor.domain.catalog.model.Option;
import ro.aciee.advisor.domain.catalog.model.ProfessorPage;
import ro.aciee.advisor.domain.catalog.model.Survival;
import ro.aciee.advisor.domain.catalog.service.CatalogService;
import ro.aciee.advisor.domain.preview.model.PreviewCard;
import ro.aciee.advisor.domain.preview.service.PreviewCardRenderer;
import ro.aciee.advisor.domain.preview.service.PreviewService;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.Timetable;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.TimetableService;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static ro.aciee.advisor.domain.timetable.model.source.WeekParity.ALL;
import static ro.aciee.advisor.domain.timetable.model.source.WeekParity.EVEN;
import static ro.aciee.advisor.domain.timetable.model.source.WeekParity.ODD;

class PreviewCardImageTests {

    private static final Path OUT = Path.of("build", "og-previews");
    private static final String[] YEAR = {"22C31A", "22C31B", "22C32A", "22C32B", "22C33A", "22C33B"};
    private static final String[] GROUP = {"22C31A", "22C31B"};
    private static final String[] HALF = {"22C31B"};

    private final CatalogService catalog = mock(CatalogService.class);
    private final TimetableService timetable = mock(TimetableService.class);
    private final PreviewService previews = new PreviewService(catalog, timetable);
    private final PreviewCardRenderer renderer = new PreviewCardRenderer();

    @Test
    void everyCardRendersAt1200By630() throws Exception {
        when(timetable.current()).thenReturn(new Timetable(null, "test", List.of(thirdYear())));
        when(catalog.course(12, null)).thenReturn(course());
        when(catalog.professor("ion-popescu")).thenReturn(professor());
        when(catalog.lookups()).thenReturn(lookups());

        var cards = new LinkedHashMap<String, PreviewCard>();
        cards.put("home", previews.cardForPath("/"));
        cards.put("courses", previews.cardForPath("/courses"));
        cards.put("professors", previews.cardForPath("/professors"));
        cards.put("timetable", previews.cardForPath("/timetable"));
        cards.put("timetable-22C31B", previews.cardForPath("/timetable", "22C31B"));
        cards.put("guide", previews.cardForPath("/guide"));
        cards.put("rate", previews.cardForPath("/rate"));
        cards.put("course", previews.cardForPath("/courses/12"));
        cards.put("professor", previews.cardForPath("/professors/ion-popescu"));

        assertThat(cards.get("timetable-22C31B").description()).isEqualTo(
                "Ore pe săptămână: 26 · Zile cu ore: 5 · Cea mai plină zi: Marți · Luni începi la: 10:00 · Săpt. impară 22 h · pară 30 h");
        Files.createDirectories(OUT);
        for (var entry : cards.entrySet()) {
            byte[] png = renderer.render(entry.getValue());
            Files.write(OUT.resolve(entry.getKey() + ".png"), png);
            var image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image.getWidth()).isEqualTo(1200);
            assertThat(image.getHeight()).isEqualTo(630);
        }
    }

    private TimetableProgram thirdYear() {
        var program = new TimetableProgram();
        program.setName("Calculatoare");
        program.setYear(3);
        program.setGroups(List.of(YEAR));
        program.setLessons(List.of(
                lesson(1, "10:00", ODD, GROUP), lesson(1, "10:00", EVEN, GROUP),
                lesson(1, "12:00", ALL, YEAR), lesson(1, "14:00", ALL, YEAR),
                lesson(2, "08:00", ALL, YEAR), lesson(2, "10:00", ALL, HALF), lesson(2, "12:00", ALL, YEAR),
                lesson(2, "14:00", ODD, GROUP), lesson(2, "14:00", EVEN, YEAR),
                lesson(3, "08:00", EVEN, GROUP), lesson(3, "10:00", EVEN, HALF), lesson(3, "12:00", EVEN, HALF),
                lesson(4, "12:00", ALL, YEAR), lesson(4, "14:00", ALL, HALF), lesson(4, "16:00", ALL, HALF),
                lesson(5, "08:00", ALL, YEAR), lesson(5, "10:00", EVEN, HALF),
                lesson(5, "12:00", ALL, "22C32A")));
        return program;
    }

    private Lesson lesson(int day, String start, WeekParity weeks, String... groups) {
        String end = start.substring(0, 1) + (char) (start.charAt(1) + 1) + ":50";
        return new Lesson(day, start, end, weeks, List.of(groups), "C", "Baze de date II", null, null, null, List.of());
    }

    private CoursePage course() {
        var survival = new Survival();
        survival.setN(23);
        survival.setExamDifficulty(6.84);
        survival.setExamForm("Scris 85%");
        survival.setWeeklyHours(4.5);
        survival.setAttendanceStrictness(7.2);
        var page = new CoursePage();
        page.setSubjectName("Baze de date II");
        page.setSpecialization("Calculatoare");
        page.setStudyYear(3);
        page.setSemesterNo(1);
        page.setSurvival(survival);
        return page;
    }

    private ProfessorPage professor() {
        var page = new ProfessorPage();
        page.setFullName("Ion Popescu");
        page.setTitle("Conf. dr. ing.");
        page.setSubjects(List.of());
        page.setRatings(31);
        page.setOverall(8.4);
        page.setLecture(8.9);
        page.setLab(7.6);
        page.setSeminar(6.9);
        page.setProject(7.2);
        page.setGrading(8.1);
        return page;
    }

    private Lookups lookups() {
        var lookups = new Lookups();
        lookups.setPillars(Stream.of("lecture:Curs", "lab:Laborator", "seminar:Seminar", "project:Proiect", "grading:Notare și comunicare").map(pair -> {
            var option = new Option();
            option.setCode(pair.split(":")[0]);
            option.setLabel(pair.split(":")[1]);
            return option;
        }).toList());
        return lookups;
    }
}
