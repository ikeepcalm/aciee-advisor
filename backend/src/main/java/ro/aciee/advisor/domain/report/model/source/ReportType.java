package ro.aciee.advisor.domain.report.model.source;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum ReportType {
    RADAR("Profesor × disciplină", List.of("Profesor", "Departament", "Disciplină", "Activitate", "n"), true, """
            SELECT professor, department, subject, component, n,
                   a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12,
                   a13, a14, a15, a16, a17, a18, a19, a20, a21, a22, a23, a24
            FROM report.prof_subject
            WHERE (CAST(:specialization AS integer) IS NULL OR :specialization = ANY (specialization_ids))
              AND (CAST(:department AS integer) IS NULL OR department_id = :department)
              AND (:locked OR n >= (SELECT k_threshold FROM ref.setting))
            ORDER BY professor, subject, component"""),
    SURVIVAL("Supraviețuire pe disciplină", List.of("Disciplină", "n", "Dificultatea examenului", "Cât ajută laboratoarele",
            "Strictețea prezenței", "Ore pe săptămână", "Prezența la curs e utilă", "Forma examenului", "Conținutul examenului"), false, """
            SELECT subject, n, exam_difficulty, labs_help, attendance_strictness, weekly_hours, attendance_useful, exam_form, exam_content
            FROM report.course_survival
            WHERE (CAST(:specialization AS integer) IS NULL OR :specialization = ANY (specialization_ids))
              AND (:locked OR n >= (SELECT k_threshold FROM ref.setting))
            ORDER BY subject""");

    private final String title;
    private final List<String> keyColumns;
    private final boolean withAxes;
    private final String sql;

    public static ReportType of(String key) {
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    }
}
