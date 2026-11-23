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
import ro.aciee.advisor.domain.preview.type.PreviewPage;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;
import ro.aciee.advisor.domain.timetable.service.TimetableService;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final Pattern COURSE_PATH = Pattern.compile("^/courses/(\\d+)/?$");
    private static final Pattern PROFESSOR_PATH = Pattern.compile("^/professors/([a-z0-9-]+)/?$");
    private static final Pattern PAGE_PATH = Pattern.compile("^/([a-z]+)(?:/.*)?$");
    private static final Pattern GROUP = Pattern.compile("^[A-Z0-9]{2,3}[0-9]{2}[A-Z]?$");
    private static final String[] DAYS = {"", "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică"};
    private static final String SITE = "ACIEE Advisor";
    private static final String FEW_RATINGS = "Încă puține evaluări. Spune cum a fost.";

    private final CatalogService catalogService;
    private final TimetableService timetableService;

    @Value("${app.public-url}")
    private String publicUrl;

    public PreviewCard cardForPath(String path) {
        return cardForPath(path, null);
    }

    public PreviewCard cardForPath(String path, String group) {
        var course = COURSE_PATH.matcher(path);
        if (course.matches()) {
            return courseCard(Integer.parseInt(course.group(1)));
        }
        var professor = PROFESSOR_PATH.matcher(path);
        if (professor.matches()) {
            return professorCard(professor.group(1));
        }
        var match = PAGE_PATH.matcher(path);
        PreviewPage page = switch (match.matches() ? match.group(1) : "") {
            case "courses" -> PreviewPage.COURSES;
            case "professors" -> PreviewPage.PROFESSORS;
            case "timetable", "orar" -> PreviewPage.TIMETABLE;
            case "guide" -> PreviewPage.GUIDE;
            case "rate" -> PreviewPage.RATE;
            case "preview" -> PreviewPage.PREVIEW;
            default -> PreviewPage.HOME;
        };
        if (page == PreviewPage.PREVIEW) {
            return new PreviewCard(page.getEyebrow(), page.getTitle(), List.of(), page.getNote(), "/preview", "/preview/og-en.jpg", page);
        }
        return page == PreviewPage.TIMETABLE ? timetableCard(path, group) : pageCard(page, path);
    }

    public PreviewCard courseCard(int itemId) {
        CoursePage page;
        try {
            page = catalogService.course(itemId, null);
        } catch (ResponseStatusException e) {
            return pageCard(PreviewPage.COURSES, "/courses");
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
                "/courses/" + itemId, "/og/img/courses/" + itemId + ".png", null);
    }

    public PreviewCard professorCard(String slug) {
        ProfessorPage page;
        try {
            page = catalogService.professor(slug);
        } catch (ResponseStatusException e) {
            return pageCard(PreviewPage.PROFESSORS, "/professors");
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
                "/professors/" + slug, "/og/img/professors/" + slug + ".png", null);
    }

    public PreviewCard timetableCard(String path, String group) {
        String code = group == null ? "" : group.toUpperCase(Locale.ROOT);
        List<Lesson> lessons = List.of();
        String eyebrow = null;
        if (GROUP.matcher(code).matches()) {
            for (var program : timetableService.current().getPrograms()) {
                if (program.getGroups().contains(code)) {
                    eyebrow = program.getName() + " · Anul " + program.getYear();
                    lessons = program.getLessons().stream().filter(lesson -> lesson.getGroups().contains(code)).toList();
                }
            }
        }
        if (lessons.isEmpty()) {
            return pageCard(PreviewPage.TIMETABLE, path);
        }
        long[] odd = new long[8];
        long[] even = new long[8];
        for (Lesson lesson : lessons) {
            long hours = Math.round(Duration.between(LocalTime.parse(lesson.getStart()), LocalTime.parse(lesson.getEnd())).toMinutes() / 60.0);
            odd[lesson.getDay()] += lesson.getWeeks() == WeekParity.EVEN ? 0 : hours;
            even[lesson.getDay()] += lesson.getWeeks() == WeekParity.ODD ? 0 : hours;
        }
        long oddTotal = 0;
        long evenTotal = 0;
        int days = 0;
        int busiest = 1;
        for (int day = 1; day <= 7; day++) {
            oddTotal += odd[day];
            evenTotal += even[day];
            days += odd[day] + even[day] > 0 ? 1 : 0;
            busiest = odd[day] + even[day] > odd[busiest] + even[busiest] ? day : busiest;
        }
        Lesson first = lessons.stream().min(Comparator.comparing(Lesson::getDay).thenComparing(Lesson::getStart)).orElseThrow();
        var metrics = List.of(
                new PreviewMetric("Ore pe săptămână", number((oddTotal + evenTotal) / 2.0).replace(".0", ""), true),
                new PreviewMetric("Zile cu ore", String.valueOf(days), false),
                new PreviewMetric("Cea mai plină zi", DAYS[busiest], false),
                new PreviewMetric(DAYS[first.getDay()] + " începi la", first.getStart(), false));
        String note = oddTotal == evenTotal ? "La fel în fiecare săptămână"
                : "Săpt. impară " + oddTotal + " h · pară " + evenTotal + " h";
        return new PreviewCard(eyebrow, "Orarul grupei " + code, metrics, note,
                path + "?g=" + code, "/og/img/timetable/" + code + ".png", PreviewPage.TIMETABLE);
    }

    public PreviewCard pageCard(PreviewPage page, String path) {
        return new PreviewCard(page.getEyebrow(), page.getTitle(), List.of(), page.getNote(),
                path, "/og/img/pages/" + page.name().toLowerCase(Locale.ROOT) + ".png", page);
    }

    public String html(PreviewCard card) {
        String url = publicUrl + card.getPath();
        String image = publicUrl + card.getImagePath();
        String title = HtmlUtils.htmlEscape(card.getTitle() + " · " + SITE, "UTF-8");
        String description = HtmlUtils.htmlEscape(card.description(), "UTF-8");
        boolean video = card.getPage() == PreviewPage.PREVIEW;
        String extra = video ? """
                <meta property="og:video" content="%1$s/preview/loop-en.mp4">
                <meta property="og:video:secure_url" content="%1$s/preview/loop-en.mp4">
                <meta property="og:video:type" content="video/mp4">
                <meta property="og:video:width" content="1280">
                <meta property="og:video:height" content="720">
                """.formatted(HtmlUtils.htmlEscape(publicUrl, "UTF-8")) : "";
        return """
                <!doctype html>
                <html lang="%6$s">
                <head>
                <meta charset="utf-8">
                <title>%1$s</title>
                <meta name="description" content="%2$s">
                <link rel="canonical" href="%3$s">
                <meta property="og:type" content="%7$s">
                <meta property="og:site_name" content="%5$s">
                <meta property="og:locale" content="%8$s">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:url" content="%3$s">
                <meta property="og:image" content="%4$s">
                <meta property="og:image:width" content="1200">
                <meta property="og:image:height" content="630">
                <meta property="og:image:alt" content="%1$s">
                %9$s<meta name="twitter:card" content="summary_large_image">
                </head>
                <body><a href="%3$s">%1$s</a></body>
                </html>
                """.formatted(title, description, HtmlUtils.htmlEscape(url, "UTF-8"), HtmlUtils.htmlEscape(image, "UTF-8"), SITE,
                video ? "en" : "ro", video ? "video.other" : "website", video ? "en_US" : "ro_RO", extra);
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
