package ro.aciee.advisor.domain.timetable.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ro.aciee.advisor.domain.timetable.model.Timetable;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TimetableService {

    private static final URI SITE = URI.create("https://aciee.ugal.ro");
    private static final URI LISTING = SITE.resolve("/studenti/orare-licenta");
    private static final Pattern PDF_LINK = Pattern.compile("href=\"(/files/studenti/orare/an_(\\d)_([A-Za-z_]+)\\.pdf)");
    private static final int MAX_PDF_BYTES = 10 * 1024 * 1024;
    private static final Map<String, String> PROGRAM_NAMES = Map.of(
            "IS", "Automatică și informatică aplicată",
            "CTI", "Calculatoare",
            "IE", "Inginerie electrică",
            "IE_IEC", "Inginerie electrică și calculatoare",
            "IE_EM", "Electromecanică",
            "IETTI", "Electronică și telecomunicații",
            "IETTI_RST", "Rețele și software de telecomunicații",
            "IETTI_EA", "Electronică aplicată");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Map<String, TimetableProgram> lastGood = new HashMap<>();
    private volatile Timetable current = new Timetable(null, LISTING.toString(), List.of());

    public Timetable current() {
        return current;
    }

    public synchronized void refresh() throws IOException, InterruptedException {
        var listing = http.send(request(LISTING).build(), HttpResponse.BodyHandlers.ofString());
        if (listing.statusCode() != 200) {
            throw new IOException("listing returned " + listing.statusCode());
        }
        var links = PDF_LINK.matcher(listing.body()).results().collect(Collectors.toMap(m -> m.group(1), m -> m, (a, b) -> a, LinkedHashMap::new));
        if (links.isEmpty()) {
            throw new IOException("no timetable links on " + LISTING);
        }
        var programs = new ArrayList<TimetableProgram>();
        for (var link : links.values()) {
            var code = "an_" + link.group(2) + "_" + link.group(3);
            try {
                lastGood.put(code, download(link, code));
            } catch (IOException | RuntimeException e) {
                log.warn("Timetable {} not updated: {}", link.group(1), e.toString());
            }
            var program = lastGood.get(code);
            if (program != null) {
                programs.add(program);
            }
        }
        programs.sort(Comparator.comparing(TimetableProgram::getYear).thenComparing(TimetableProgram::getCode));
        current = new Timetable(Instant.now(), LISTING.toString(), List.copyOf(programs));
    }

    private TimetableProgram download(MatchResult link, String code) throws IOException, InterruptedException {
        var uri = SITE.resolve(link.group(1));
        var previous = lastGood.get(code);
        var request = request(uri);
        if (previous != null && previous.getUpdatedAt() != null) {
            request.header("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME.format(previous.getUpdatedAt().atZone(ZoneOffset.UTC)));
        }
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() == 304 && previous != null) {
                return previous;
            }
            if (response.statusCode() != 200) {
                throw new IOException("status " + response.statusCode());
            }
            var program = parse(body, uri, code, Integer.parseInt(link.group(2)), link.group(3));
            program.setUpdatedAt(lastModified(response));
            return program;
        }
    }

    // kept out of lastGood: an archived an_1_CTI must never replace this year's an_1_CTI
    public TimetableProgram archived(int startYear, int semester, int studyYear, String program) throws IOException, InterruptedException {
        var uri = SITE.resolve("/files/studenti/orare/%d_%d/sem%d/an_%d_%s.pdf".formatted(startYear, startYear + 1, semester, studyYear, program));
        var response = http.send(request(uri).build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) {
                return null;
            }
            return parse(body, uri, "an_" + studyYear + "_" + program, studyYear, program);
        }
    }

    private static TimetableProgram parse(InputStream body, URI uri, String code, int year, String program) throws IOException {
        var pdf = body.readNBytes(MAX_PDF_BYTES + 1);
        if (pdf.length > MAX_PDF_BYTES) {
            throw new IOException("larger than " + MAX_PDF_BYTES + " bytes");
        }
        var parsed = TimetableParser.parse(pdf);
        parsed.setCode(code);
        parsed.setYear(year);
        parsed.setName(PROGRAM_NAMES.getOrDefault(program, program.replace('_', ' ')));
        parsed.setPdfUrl(uri.toString());
        LessonNames.attachCourseNames(parsed);
        return parsed;
    }

    private static Instant lastModified(HttpResponse<?> response) {
        try {
            return response.headers().firstValue("Last-Modified")
                    .map(value -> ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                    .orElse(null);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", "ACIEE Advisor timetable sync");
    }
}
