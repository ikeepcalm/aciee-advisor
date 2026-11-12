package ro.aciee.advisor.domain.timetable.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final Pattern ACADEMIC_YEAR = Pattern.compile("(\\d{4})\\s*-\\s*\\d{4}");

    private final TimetableService timetableService;
    private final JdbcClient jdbc;

    @Value("${app.public-url}")
    private String publicUrl;

    private record SemesterDates(LocalDate firstDay, OffsetDateTime ratingOpensAt) {
    }

    @Transactional(readOnly = true)
    public String calendar(String programCode, String group, LocalDate oddWeek) {
        LocalDate today = LocalDate.now(CalendarWriter.BUCHAREST);
        if (oddWeek.isBefore(today.minusYears(1)) || oddWeek.isAfter(today.plusYears(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_week");
        }
        TimetableProgram program = timetableService.current().getPrograms().stream()
                .filter(candidate -> candidate.getCode().equals(programCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        if (!program.getGroups().contains(group)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
        SemesterDates semester = semester(program);
        LocalDate ratingOpens = semester.ratingOpensAt().atZoneSameInstant(CalendarWriter.BUCHAREST).toLocalDate();
        return CalendarWriter.write(program, group, oddWeek.with(DayOfWeek.MONDAY), semester.firstDay(),
                ratingOpens.minusDays(1), ratingOpens, publicUrl);
    }

    private SemesterDates semester(TimetableProgram program) {
        var year = ACADEMIC_YEAR.matcher(program.getSemester() == null ? "" : program.getSemester());
        if (!year.find()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "semester_unknown");
        }
        return jdbc.sql("""
                        SELECT lower(s.during) AS first_day, s.rating_opens_at
                        FROM core.semester s
                        JOIN core.academic_year ay ON ay.id = s.academic_year_id
                        WHERE extract(YEAR FROM lower(ay.during)) = :year AND s.no = :no""")
                .param("year", Integer.parseInt(year.group(1)))
                .param("no", LessonNames.semesterNo(program))
                .query(SemesterDates.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "semester_unknown"));
    }
}
