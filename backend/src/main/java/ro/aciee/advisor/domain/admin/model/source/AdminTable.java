package ro.aciee.advisor.domain.admin.model.source;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.admin.model.AdminColumn;

import java.util.Arrays;
import java.util.List;

@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AdminTable {
    FACULTY("core.faculty", "name_ro",
            text("code"), text("name_ro")),
    DEPARTMENT("core.department", "name_ro",
            ref("faculty_id", "faculty"), text("name_ro")),
    SPECIALIZATION("core.specialization", "name_ro",
            ref("faculty_id", "faculty"), text("code"), text("name_ro"), text("degree"), number("duration_years")),
    SUBJECT("core.subject", "code || ' · ' || name_ro",
            text("code"), text("name_ro"), optional("name_en", "text"), optional("replaced_by_id", "integer", "subject")),
    STUDY_PLAN("core.study_plan",
            "(SELECT s.name_ro FROM core.specialization s WHERE s.id = specialization_id) || ' ' || intake::text",
            ref("specialization_id", "specialization"), column("intake", "int4range")),
    CURRICULUM_ITEM("core.curriculum_item",
            "(SELECT s.name_ro FROM core.subject s WHERE s.id = subject_id) || ' · anul ' || study_year || ', sem. ' || semester_no",
            ref("plan_id", "study_plan"), ref("subject_id", "subject"), number("study_year"), number("semester_no"),
            optional("elective_slot", "smallint")),
    ACADEMIC_YEAR("core.academic_year", "during::text",
            column("during", "daterange")),
    SEMESTER("core.semester", "(SELECT a.during::text FROM core.academic_year a WHERE a.id = academic_year_id) || ' · sem. ' || no",
            ref("academic_year_id", "academic_year"), number("no"), column("during", "daterange"),
            column("rating_opens_at", "timestamptz")),
    STUDY_GROUP("core.study_group", "code || ' · ' || (SELECT a.during::text FROM core.academic_year a WHERE a.id = academic_year_id)",
            ref("plan_id", "study_plan"), ref("academic_year_id", "academic_year"), number("study_year"), text("code"),
            number("max_size")),
    SUBGROUP("core.subgroup", "(SELECT g.code FROM core.study_group g WHERE g.id = group_id) || ' / ' || label",
            ref("group_id", "study_group"), text("label")),
    COURSE_OFFERING("core.course_offering",
            "(SELECT g.code FROM core.study_group g WHERE g.id = group_id) || ' · ' || title_snapshot",
            ref("group_id", "study_group"), ref("curriculum_item_id", "curriculum_item"), text("title_snapshot"),
            derived("plan_id", "(SELECT plan_id FROM core.study_group WHERE id = CAST(:group_id AS integer))"),
            derived("study_year", "(SELECT study_year FROM core.study_group WHERE id = CAST(:group_id AS integer))")),
    PROFESSOR("core.professor", "full_name",
            text("full_name"), ref("title_code", "academic_title"), ref("department_id", "department"), text("slug"),
            readOnly("archived_at", "timestamptz")),
    TEACHING_ASSIGNMENT("core.teaching_assignment",
            "(SELECT p.full_name FROM core.professor p WHERE p.id = professor_id) || ' · ' || component"
                    + " || ' · ' || (SELECT o.title_snapshot FROM core.course_offering o WHERE o.id = offering_id)",
            ref("offering_id", "course_offering"), ref("professor_id", "professor"), ref("component", "component"),
            optional("subgroup_id", "integer", "subgroup"), column("valid_during", "daterange"),
            derived("group_id", "(SELECT group_id FROM core.course_offering WHERE id = CAST(:offering_id AS integer))")),
    ACADEMIC_TITLE("ref.academic_title", "label_ro"),
    COMPONENT("ref.component", "label_ro");

    @JsonIgnore
    private final String table;
    @JsonIgnore
    private final String label;
    private final List<AdminColumn> columns;

    AdminTable(String table, String label, AdminColumn... columns) {
        this.table = table;
        this.label = label;
        this.columns = List.of(columns);
    }

    public String getKey() {
        return name().toLowerCase();
    }

    public String getKeyColumn() {
        return table.startsWith("ref.") ? "code" : "id";
    }

    public boolean isEditable() {
        return !columns.isEmpty();
    }

    public static AdminTable of(String key) {
        return Arrays.stream(values())
                .filter(table -> table.getKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "table_not_found"));
    }

    private static AdminColumn text(String name) {
        return column(name, "text");
    }

    private static AdminColumn number(String name) {
        return column(name, "smallint");
    }

    private static AdminColumn column(String name, String type) {
        return new AdminColumn(name, type, null, false, false, null);
    }

    private static AdminColumn ref(String name, String table) {
        return new AdminColumn(name, name.endsWith("_code") || table.equals("component") ? "text" : "integer", table, false, false, null);
    }

    private static AdminColumn optional(String name, String type) {
        return new AdminColumn(name, type, null, true, false, null);
    }

    private static AdminColumn optional(String name, String type, String table) {
        return new AdminColumn(name, type, table, true, false, null);
    }

    private static AdminColumn readOnly(String name, String type) {
        return new AdminColumn(name, type, null, true, true, null);
    }

    private static AdminColumn derived(String name, String sql) {
        return new AdminColumn(name, "text", null, true, true, sql);
    }
}
