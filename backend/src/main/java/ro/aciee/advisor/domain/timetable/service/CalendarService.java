package ro.aciee.advisor.domain.timetable.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final Pattern ACADEMIC_YEAR = Pattern.compile("(\\d{4})\\s*-\\s*\\d{4}");

    private final TimetableService timetableService;
    private final JdbcClient jdbc;

    @Value("${app.public-url}")
    private String publicUrl;

    private record Semester(int id, LocalDate ratingOpens) {
    }

    @Transactional(readOnly = true)
    public String calendar(String programCode, String group) {
        TimetableProgram program = timetableService.current().getPrograms().stream()
                .filter(candidate -> candidate.getCode().equals(programCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        if (!program.getGroups().contains(group)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
        Semester semester = semester(program)
                .filter(found -> !program.getWeeks().isEmpty())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "semester_unknown"));
        return CalendarWriter.write(program, group, program.getWeeks(), semester.ratingOpens(), publicUrl);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, WeekParity> weeks(TimetableProgram program) {
        return semester(program).map(this::weeks).orElse(Map.of());
    }

    private Map<LocalDate, WeekParity> weeks(Semester semester) {
        List<LocalDate> mondays = jdbc.sql("""
                        SELECT w::date
                        FROM core.semester s,
                             generate_series(date_trunc('week', lower(s.during)::timestamp), (:ratingOpens::date - 1)::timestamp, interval '1 week') w
                        WHERE s.id = :id
                          AND EXISTS (SELECT FROM generate_series(w, w + interval '4 days', interval '1 day') d
                                      WHERE NOT coalesce(s.breaks, '{}') @> d::date)
                        ORDER BY w""")
                .param("id", semester.id())
                .param("ratingOpens", semester.ratingOpens())
                .query(LocalDate.class)
                .list();
        var weeks = new LinkedHashMap<LocalDate, WeekParity>();
        for (int index = 0; index < mondays.size(); index++) {
            weeks.put(mondays.get(index), index % 2 == 0 ? WeekParity.ODD : WeekParity.EVEN);
        }
        return weeks;
    }

    private Optional<Semester> semester(TimetableProgram program) {
        var year = ACADEMIC_YEAR.matcher(program.getSemester() == null ? "" : program.getSemester());
        if (!year.find()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT s.id, (s.rating_opens_at AT TIME ZONE 'Europe/Bucharest')::date AS rating_opens
                        FROM core.semester s
                        JOIN core.academic_year ay ON ay.id = s.academic_year_id
                        WHERE extract(YEAR FROM lower(ay.during)) = :year AND s.no = :no""")
                .param("year", Integer.parseInt(year.group(1)))
                .param("no", LessonNames.semesterNo(program))
                .query(Semester.class)
                .optional();
    }
}
