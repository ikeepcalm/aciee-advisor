package ro.aciee.advisor.domain.preview;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.catalog.model.CoursePage;
import ro.aciee.advisor.domain.catalog.model.Survival;
import ro.aciee.advisor.domain.catalog.service.CatalogService;
import ro.aciee.advisor.domain.preview.service.PreviewCardRenderer;
import ro.aciee.advisor.domain.preview.service.PreviewService;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.Timetable;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.TimetableService;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewServiceTests {

    private final CatalogService catalog = mock(CatalogService.class);
    private final TimetableService timetable = mock(TimetableService.class);
    private final PreviewService previews = new PreviewService(catalog, timetable);

    PreviewServiceTests() {
        ReflectionTestUtils.setField(previews, "publicUrl", "https://acieeadvisor.ro");
    }

    @Test
    void previewPageCarriesTheLoopVideoInEnglish() {
        String html = previews.html(previews.cardForPath("/preview"));

        assertThat(html).contains("<html lang=\"ro\">", "content=\"video.other\"", "content=\"ro_RO\"",
                "og:video\" content=\"https://acieeadvisor.ro/preview/loop-ro.mp4\"",
                "og:image\" content=\"https://acieeadvisor.ro/preview/og-ro.jpg\"");
        assertThat(previews.html(previews.cardForPath("/courses"))).doesNotContain("og:video").contains("content=\"website\"");
    }

    @Test
    void courseBelowThresholdShowsNoNumbers() {
        var page = course("Baze de date");
        var survival = new Survival();
        survival.setN(3);
        page.setSurvival(survival);
        when(catalog.course(12, null)).thenReturn(page);

        var card = previews.cardForPath("/courses/12");

        assertThat(card.getMetrics()).isEmpty();
        assertThat(card.getNote()).startsWith("Încă puține evaluări");
        assertThat(card.getImagePath()).isEqualTo("/og/img/courses/12.png");
    }

    @Test
    void courseAboveThresholdListsSurvivalMetrics() {
        var page = course("Baze de date");
        var survival = new Survival();
        survival.setN(23);
        survival.setExamDifficulty(6.84);
        survival.setExamFormat("Scris");
        page.setSurvival(survival);
        when(catalog.course(12, null)).thenReturn(page);

        var card = previews.cardForPath("/courses/12");

        assertThat(card.description()).isEqualTo("Dificultate examen: 6.8/10 · Format examen: Scris · 23 evaluări");
    }

    @Test
    void eachPageGetsItsOwnCardAndUnknownPathsFallBackToHome() {
        when(catalog.course(anyInt(), isNull())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(catalog.professor("nobody")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(timetable.current()).thenReturn(new Timetable(null, "test", List.of()));

        assertThat(previews.cardForPath("/").getImagePath()).isEqualTo("/og/img/pages/home.png");
        assertThat(previews.cardForPath("/courses").getImagePath()).isEqualTo("/og/img/pages/courses.png");
        assertThat(previews.cardForPath("/courses/999").getImagePath()).isEqualTo("/og/img/pages/courses.png");
        assertThat(previews.cardForPath("/professors/").getImagePath()).isEqualTo("/og/img/pages/professors.png");
        assertThat(previews.cardForPath("/professors/nobody").getImagePath()).isEqualTo("/og/img/pages/professors.png");
        assertThat(previews.cardForPath("/timetable").getTitle()).isEqualTo("Orarul grupei tale");
        assertThat(previews.cardForPath("/orar").getImagePath()).isEqualTo("/og/img/pages/timetable.png");
        assertThat(previews.cardForPath("/guide").getImagePath()).isEqualTo("/og/img/pages/guide.png");
        assertThat(previews.cardForPath("/rate/41").getImagePath()).isEqualTo("/og/img/pages/rate.png");
        assertThat(previews.cardForPath("/signin").getImagePath()).isEqualTo("/og/img/pages/home.png");
        assertThat(previews.cardForPath("/coursesx").getImagePath()).isEqualTo("/og/img/pages/home.png");
        assertThat(previews.cardForPath("/Courses/12/../../admin").getImagePath()).isEqualTo("/og/img/pages/home.png");
    }

    @Test
    void timetableGroupGetsItsOwnCardWithParityCountedPerWeek() {
        var program = new TimetableProgram();
        program.setName("Calculatoare");
        program.setYear(3);
        program.setGroups(List.of("22C31A", "22C31B"));
        program.setLessons(List.of(
                lesson(2, "08:00", "09:50", WeekParity.ALL, "22C31A", "22C31B"),
                lesson(2, "10:00", "11:50", WeekParity.ODD, "22C31B"),
                lesson(2, "10:00", "11:50", WeekParity.EVEN, "22C31B"),
                lesson(4, "12:00", "13:50", WeekParity.EVEN, "22C31B"),
                lesson(1, "08:00", "13:50", WeekParity.ALL, "22C31A")));
        when(timetable.current()).thenReturn(new Timetable(null, "test", List.of(program)));

        var card = previews.cardForPath("/timetable", "22c31b");

        assertThat(card.getEyebrow()).isEqualTo("Calculatoare · Anul 3");
        assertThat(card.getTitle()).isEqualTo("Orarul grupei 22C31B");
        assertThat(card.description()).isEqualTo(
                "Ore pe săptămână: 5 · Zile cu ore: 2 · Cea mai plină zi: Marți · Marți începi la: 08:00 · Săpt. impară 4 h · pară 6 h");
        assertThat(card.getImagePath()).isEqualTo("/og/img/timetable/22C31B.png");
        assertThat(previews.html(card)).contains("<meta property=\"og:url\" content=\"https://acieeadvisor.ro/timetable?g=22C31B\">");
        assertThat(previews.cardForPath("/orar", "22C31A").getPath()).isEqualTo("/orar?g=22C31A");
        assertThat(previews.cardForPath("/courses", "22C31B").getImagePath()).isEqualTo("/og/img/pages/courses.png");
    }

    @Test
    void unknownOrMalformedGroupFallsBackToTheTimetableCard() {
        var program = new TimetableProgram();
        program.setGroups(List.of("22C31B", "<b>"));
        program.setLessons(List.of(lesson(1, "08:00", "09:50", WeekParity.ALL, "<b>")));
        when(timetable.current()).thenReturn(new Timetable(null, "test", List.of(program)));

        for (String group : new String[]{"22C99Z", "22C31B", "<b>", "../admin", "", "22C31B&x=1"}) {
            var card = previews.cardForPath("/timetable", group);
            assertThat(card.getImagePath()).isEqualTo("/og/img/pages/timetable.png");
            assertThat(card.getPath()).isEqualTo("/timetable");
        }
    }

    @Test
    void htmlEscapesNamesAndUsesAbsoluteUrls() {
        when(catalog.course(12, null)).thenReturn(course("<script>alert(1)</script> & co"));

        String html = previews.html(previews.cardForPath("/courses/12"));

        assertThat(html).doesNotContain("<script>alert");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; co · ACIEE Advisor");
        assertThat(html).contains("<meta property=\"og:image\" content=\"https://acieeadvisor.ro/og/img/courses/12.png\">");
        assertThat(html).contains("<meta property=\"og:url\" content=\"https://acieeadvisor.ro/courses/12\">");
    }

    @Test
    void rendersAPngOfTheExpectedSize() throws Exception {
        var page = course("Programarea calculatoarelor și limbaje de programare, partea a doua");
        when(catalog.course(12, null)).thenReturn(page);

        byte[] png = new PreviewCardRenderer().render(previews.cardForPath("/courses/12"));
        var image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(630);
    }

    private Lesson lesson(int day, String start, String end, WeekParity weeks, String... groups) {
        return new Lesson(day, start, end, weeks, List.of(groups), "C", "Baze de date", null, null, null, List.of());
    }

    private CoursePage course(String name) {
        var page = new CoursePage();
        page.setSubjectName(name);
        page.setSpecialization("Calculatoare");
        page.setStudyYear(2);
        page.setSemesterNo(1);
        return page;
    }
}
