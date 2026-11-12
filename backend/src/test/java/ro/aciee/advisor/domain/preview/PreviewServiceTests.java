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

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewServiceTests {

    private final CatalogService catalog = mock(CatalogService.class);
    private final PreviewService previews = new PreviewService(catalog);

    PreviewServiceTests() {
        ReflectionTestUtils.setField(previews, "publicUrl", "https://acieeadvisor.ro");
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
    void unknownCourseAndOtherPathsFallBackToTheDefaultCard() {
        when(catalog.course(anyInt(), isNull())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(previews.cardForPath("/courses/999").getImagePath()).isEqualTo("/og/img/default.png");
        assertThat(previews.cardForPath("/timetable").getTitle()).isEqualTo("Orarul grupei tale");
        assertThat(previews.cardForPath("/courses/12/../../admin").getImagePath()).isEqualTo("/og/img/default.png");
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

    private CoursePage course(String name) {
        var page = new CoursePage();
        page.setSubjectName(name);
        page.setSpecialization("Calculatoare");
        page.setStudyYear(2);
        page.setSemesterNo(1);
        return page;
    }
}
