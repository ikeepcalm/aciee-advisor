package ro.aciee.advisor.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.admin.model.AdminColumn;
import ro.aciee.advisor.domain.admin.model.source.AdminTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTableService {

    private static final int PAGE = 50;

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(AdminTable table, String search, String after) {
        var columns = new ArrayList<String>();
        columns.add("t.%s::text AS id".formatted(table.getKeyColumn()));
        columns.add("(%s) AS label".formatted(table.getLabel()));
        for (var column : table.getColumns()) {
            columns.add("t.%s::text AS %s".formatted(column.getName(), column.getName()));
            if (column.getRef() != null) {
                var ref = AdminTable.of(column.getRef());
                columns.add("(SELECT (%s) FROM %s r WHERE r.%s = t.%s) AS %s_label"
                        .formatted(ref.getLabel(), ref.getTable(), ref.getKeyColumn(), column.getName(), column.getName()));
            }
        }
        return jdbc.sql("""
                        SELECT %s FROM %s t
                        WHERE (CAST(:search AS text) IS NULL OR (%s) ILIKE '%%' || CAST(:search AS text) || '%%')
                          AND (CAST(:after AS text) IS NULL OR t.%s > CAST(CAST(:after AS text) AS %s))
                        ORDER BY t.%s LIMIT %d"""
                        .formatted(String.join(", ", columns), table.getTable(), table.getLabel(),
                                table.getKeyColumn(), keyType(table), table.getKeyColumn(), PAGE))
                .param("search", blankToNull(search))
                .param("after", blankToNull(after))
                .query()
                .listOfRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> options(AdminTable table, String search) {
        return jdbc.sql("""
                        SELECT %s::text AS value, (%s) AS label FROM %s
                        WHERE CAST(:search AS text) IS NULL OR (%s) ILIKE '%%' || CAST(:search AS text) || '%%'
                        ORDER BY label LIMIT %d"""
                        .formatted(table.getKeyColumn(), table.getLabel(), table.getTable(), table.getLabel(), PAGE))
                .param("search", blankToNull(search))
                .query()
                .listOfRows();
    }

    @Transactional
    public String create(AdminTable table, Map<String, String> values) {
        var written = writtenColumns(requireEditable(table));
        var id = bind(jdbc.sql("INSERT INTO %s (%s) VALUES (%s) RETURNING %s::text".formatted(
                        table.getTable(),
                        written.stream().map(AdminColumn::getName).collect(Collectors.joining(", ")),
                        written.stream().map(this::valueSql).collect(Collectors.joining(", ")),
                        table.getKeyColumn())), table, values)
                .query(String.class)
                .single();
        checkSemesterInsideYear(table, id);
        return id;
    }

    @Transactional
    public void update(AdminTable table, String id, Map<String, String> values) {
        var assignments = writtenColumns(requireEditable(table)).stream()
                .map(column -> column.getName() + " = " + valueSql(column))
                .collect(Collectors.joining(", "));
        int updated = bind(jdbc.sql("UPDATE %s SET %s WHERE %s = CAST(:id AS %s)".formatted(
                        table.getTable(), assignments, table.getKeyColumn(), keyType(table))), table, values)
                .param("id", id)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
        checkSemesterInsideYear(table, id);
    }

    @Transactional
    public void delete(AdminTable table, String id) {
        int deleted = jdbc.sql("DELETE FROM %s WHERE %s = CAST(:id AS %s)".formatted(
                        requireEditable(table).getTable(), table.getKeyColumn(), keyType(table)))
                .param("id", id)
                .update();
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    @Transactional
    public void archiveProfessor(int id) {
        boolean archived = jdbc.sql("SELECT core.archive_professor(:id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
        if (!archived) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    @Transactional
    public int createOfferings(int groupId) {
        return jdbc.sql("""
                        INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
                        SELECT ci.id, g.id, g.plan_id, g.study_year, s.name_ro
                        FROM core.study_group g
                        JOIN core.curriculum_item ci ON ci.plan_id = g.plan_id AND ci.study_year = g.study_year
                        JOIN core.subject s ON s.id = ci.subject_id
                        WHERE g.id = :group
                        ON CONFLICT (curriculum_item_id, group_id) DO NOTHING""")
                .param("group", groupId)
                .update();
    }

    @Transactional
    public int rollover(int academicYearId) {
        return jdbc.sql("SELECT core.rollover(:year)")
                .param("year", academicYearId)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "next_year_missing"));
    }

    private void checkSemesterInsideYear(AdminTable table, String id) {
        if (table != AdminTable.SEMESTER) {
            return;
        }
        boolean inside = jdbc.sql("""
                        SELECT a.during @> s.during FROM core.semester s
                        JOIN core.academic_year a ON a.id = s.academic_year_id WHERE s.id = CAST(:id AS integer)""")
                .param("id", id)
                .query(Boolean.class)
                .single();
        if (!inside) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semester_outside_year");
        }
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, AdminTable table, Map<String, String> values) {
        for (var column : table.getColumns()) {
            if (column.isEditable()) {
                statement = statement.param(column.getName(), blankToNull(values.get(column.getName())));
            }
        }
        return statement;
    }

    private List<AdminColumn> writtenColumns(AdminTable table) {
        return table.getColumns().stream().filter(column -> column.isEditable() || column.getDerivedFrom() != null).toList();
    }

    private String valueSql(AdminColumn column) {
        return column.getDerivedFrom() != null ? column.getDerivedFrom() : "CAST(:%s AS %s)".formatted(column.getName(), column.getType());
    }

    private AdminTable requireEditable(AdminTable table) {
        if (!table.isEditable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "table_not_found");
        }
        return table;
    }

    private String keyType(AdminTable table) {
        return table.getKeyColumn().equals("id") ? "integer" : "text";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
