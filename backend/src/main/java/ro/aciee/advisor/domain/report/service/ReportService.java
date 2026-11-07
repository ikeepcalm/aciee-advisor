package ro.aciee.advisor.domain.report.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.aciee.advisor.domain.catalog.model.Option;
import ro.aciee.advisor.domain.report.model.ReportFilters;
import ro.aciee.advisor.domain.report.model.ReportSection;
import ro.aciee.advisor.domain.report.model.ReportTable;
import ro.aciee.advisor.domain.report.model.source.ReportType;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {

    private final JdbcClient jdbc;
    private final JdbcClient reportJdbc;

    public ReportService(JdbcClient jdbc, @Qualifier("report") DataSource reportDataSource) {
        this.jdbc = jdbc;
        this.reportJdbc = JdbcClient.create(reportDataSource);
    }

    @Transactional(readOnly = true)
    public ReportFilters filters() {
        return new ReportFilters(
                jdbc.sql("SELECT id::text AS code, name_ro AS label FROM core.specialization ORDER BY name_ro").query(Option.class).list(),
                jdbc.sql("SELECT id::text AS code, name_ro AS label FROM core.department ORDER BY name_ro").query(Option.class).list());
    }

    public ReportTable table(ReportType type, Integer specialization, Integer department, boolean locked) {
        var columns = new ArrayList<>(type.getKeyColumns());
        var sections = new ArrayList<ReportSection>();
        if (type.isWithAxes()) {
            addAxes(columns, sections);
        }
        var rows = reportJdbc.sql(type.getSql())
                .param("specialization", specialization, Types.INTEGER)
                .param("department", department, Types.INTEGER)
                .param("locked", locked)
                .query((rs, rowNum) -> {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) {
                        row.add(rs.getObject(i));
                    }
                    return row;
                })
                .list();
        return new ReportTable(type.getTitle(), type.getKeyColumns().size(), columns, sections, rows);
    }

    private void addAxes(List<String> columns, List<ReportSection> sections) {
        reportJdbc.sql("""
                        SELECT a.label_ro AS axis, p.label_ro AS pillar
                        FROM ref.axis a JOIN ref.pillar p ON p.code = a.pillar_code
                        ORDER BY a.code""")
                .query(rs -> {
                    var pillar = rs.getString("pillar");
                    if (sections.isEmpty() || !sections.getLast().getTitle().equals(pillar)) {
                        sections.add(new ReportSection(pillar, columns.size(), columns.size()));
                    }
                    columns.add(rs.getString("axis"));
                    sections.getLast().setToColumn(columns.size());
                });
    }
}
