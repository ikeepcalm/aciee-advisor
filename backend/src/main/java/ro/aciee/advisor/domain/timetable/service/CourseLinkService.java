package ro.aciee.advisor.domain.timetable.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourseLinkService {

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public void attach(TimetableProgram program) {
        var items = new HashMap<String, Integer>();
        jdbc.sql("""
                        SELECT s.name_ro AS name, ci.id
                        FROM core.curriculum_item ci
                        JOIN core.subject s ON s.id = ci.subject_id
                        JOIN core.study_plan pl ON pl.id = ci.plan_id
                        JOIN core.specialization sp ON sp.id = pl.specialization_id
                        WHERE sp.name_ro = :specialization AND ci.study_year = :year AND ci.semester_no = :semester
                        ORDER BY lower(pl.intake) DESC""")
                .param("specialization", program.getName())
                .param("year", program.getYear())
                .param("semester", LessonNames.semesterNo(program))
                .query((rs, row) -> Map.entry(LessonNames.fold(rs.getString("name")), rs.getInt("id")))
                .list()
                .forEach(entry -> items.putIfAbsent(entry.getKey(), entry.getValue()));
        for (Lesson lesson : program.getLessons()) {
            lesson.setCourseId(items.get(LessonNames.fold(lesson.getCourse())));
        }
    }
}
