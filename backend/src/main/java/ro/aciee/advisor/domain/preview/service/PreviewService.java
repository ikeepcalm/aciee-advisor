package ro.aciee.advisor.domain.preview.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import ro.aciee.advisor.domain.catalog.model.CoursePage;
import ro.aciee.advisor.domain.catalog.model.Option;
import ro.aciee.advisor.domain.catalog.model.ProfessorPage;
import ro.aciee.advisor.domain.catalog.model.Survival;
import ro.aciee.advisor.domain.catalog.service.CatalogService;
import ro.aciee.advisor.domain.preview.model.PreviewCard;
import ro.aciee.advisor.domain.preview.model.PreviewMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final Pattern COURSE_PATH = Pattern.compile("^/courses/(\\d+)/?$");
    private static final Pattern PROFESSOR_PATH = Pattern.compile("^/professors/([a-z0-9-]+)/?$");
    private static final String SITE = "ACIEE Advisor";
    private static final String FEW_RATINGS = "Încă puține evaluări. Spune cum a fost.";

    private final CatalogService catalogService;

    @Value("${app.public-url}")
    private String publicUrl;

    public PreviewCard cardForPath(String path) {
        var course = COURSE_PATH.matcher(path);
        if (course.matches()) {
            return courseCard(Integer.parseInt(course.group(1)));
        }
        var professor = PROFESSOR_PATH.matcher(path);
        if (professor.matches()) {
            return professorCard(professor.group(1));
        }
        return defaultCard(path);
    }

    public PreviewCard courseCard(int itemId) {
        CoursePage page;
        try {
            page = catalogService.course(itemId, null);
        } catch (ResponseStatusException e) {
            return defaultCard("/courses");
        }
        var metrics = new ArrayList<PreviewMetric>();
        Survival survival = page.getSurvival();
        if (survival != null && survival.getExamDifficulty() != null) {
            metrics.add(new PreviewMetric("Dificultate examen", score(survival.getExamDifficulty()), true));
            if (survival.getExamFormat() != null) {
                metrics.add(new PreviewMetric("Format examen", survival.getExamFormat(), false));
            }
            if (survival.getWeeklyHours() != null) {
                metrics.add(new PreviewMetric("Ore pe săptămână", number(survival.getWeeklyHours()), false));
            }
            if (survival.getAttendanceStrictness() != null) {
                metrics.add(new PreviewMetric("Strictețe prezență", score(survival.getAttendanceStrictness()), false));
            }
        }
        String note = metrics.isEmpty() ? FEW_RATINGS : ratings(survival.getN());
        String eyebrow = page.getSpecialization() + " · Anul " + page.getStudyYear() + " · Sem. " + page.getSemesterNo();
        return new PreviewCard(eyebrow, page.getSubjectName(), metrics, note,
                "/courses/" + itemId, "/og/img/courses/" + itemId + ".png");
    }

    public PreviewCard professorCard(String slug) {
        ProfessorPage page;
        try {
            page = catalogService.professor(slug);
        } catch (ResponseStatusException e) {
            return defaultCard("/courses");
        }
        var metrics = new ArrayList<PreviewMetric>();
        if (page.getOverall() != null) {
            metrics.add(new PreviewMetric("Scor general", score(page.getOverall()), true));
            for (Option pillar : catalogService.lookups().getPillars()) {
                Double average = switch (pillar.getCode()) {
                    case "teaching" -> page.getTeaching();
                    case "practice" -> page.getPractice();
                    case "grading" -> page.getGrading();
                    default -> null;
                };
                if (average != null) {
                    metrics.add(new PreviewMetric(pillar.getLabel(), score(average), false));
                }
            }
        }
        String note = metrics.isEmpty() ? FEW_RATINGS : ratings(page.getRatings());
        String eyebrow = page.getTitle() + " · " + page.getSubjects().size() + " discipline";
        return new PreviewCard(eyebrow, page.getFullName(), metrics, note,
                "/professors/" + slug, "/og/img/professors/" + slug + ".png");
    }

    public PreviewCard defaultCard(String path) {
        String title;
        if (path.startsWith("/timetable") || path.startsWith("/orar")) {
            title = "Orarul grupei tale";
        } else if (path.startsWith("/professors")) {
            title = "Profesorii, notați de studenți";
        } else if (path.startsWith("/guide")) {
            title = "Ghidul de anul I: credite, restanțe, burse";
        } else {
            title = "Ce îți cere de fapt fiecare curs";
        }
        return new PreviewCard("Ghidul studentului de la ACIEE", title, List.of(),
                "Evaluări anonime și sfaturi de la studenți",
                path, "/og/img/default.png");
    }

    public String html(PreviewCard card) {
        String url = publicUrl + card.getPath();
        String image = publicUrl + card.getImagePath();
        String title = HtmlUtils.htmlEscape(card.getTitle() + " · " + SITE, "UTF-8");
        String description = HtmlUtils.htmlEscape(card.description(), "UTF-8");
        return """
                <!doctype html>
                <html lang="ro">
                <head>
                <meta charset="utf-8">
                <title>%1$s</title>
                <meta name="description" content="%2$s">
                <link rel="canonical" href="%3$s">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="%5$s">
                <meta property="og:locale" content="ro_RO">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:url" content="%3$s">
                <meta property="og:image" content="%4$s">
                <meta property="og:image:width" content="1200">
                <meta property="og:image:height" content="630">
                <meta property="og:image:alt" content="%1$s">
                <meta name="twitter:card" content="summary_large_image">
                </head>
                <body><a href="%3$s">%1$s</a></body>
                </html>
                """.formatted(title, description, HtmlUtils.htmlEscape(url, "UTF-8"), HtmlUtils.htmlEscape(image, "UTF-8"), SITE);
    }

    private String score(double value) {
        return number(value) + "/10";
    }

    private String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String ratings(int n) {
        return n == 1 ? "1 evaluare" : n + " evaluări";
    }
}
