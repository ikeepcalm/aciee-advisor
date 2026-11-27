package ro.aciee.advisor.domain.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ro.aciee.advisor.TestcontainersConfiguration;
import ro.aciee.advisor.domain.report.model.source.ReportFormat;
import ro.aciee.advisor.domain.report.model.source.ReportType;
import ro.aciee.advisor.domain.report.service.ReportFileWriter;
import ro.aciee.advisor.domain.report.service.ReportService;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(properties = {"spring.liquibase.contexts=fixtures", "spring.datasource.username=test", "spring.datasource.password=test"})
@Import(TestcontainersConfiguration.class)
class ReportTests {

    @Autowired
    ReportService reportService;

    @Autowired
    ReportFileWriter reportFileWriter;

    @Autowired
    @Qualifier("report")
    DataSource reportDataSource;

    @Test
    void radarHasOneSectionPerPillarAndHidesLockedPairs() {
        var unlocked = reportService.table(ReportType.RADAR, null, null, false);
        var all = reportService.table(ReportType.RADAR, null, null, true);

        assertThat(unlocked.getColumns()).hasSize(29);
        assertThat(unlocked.getSections()).extracting("fromColumn", "toColumn")
                .containsExactly(tuple(5, 9), tuple(9, 14), tuple(14, 20), tuple(20, 24), tuple(24, 29));
        assertThat(unlocked.getRows()).isNotEmpty().allSatisfy(row -> assertThat((Long) row.get(4)).isGreaterThanOrEqualTo(5));
        assertThat(all.getRows()).hasSizeGreaterThanOrEqualTo(unlocked.getRows().size());
    }

    @Test
    void filesStartWithTheirFormatSignature() {
        var table = reportService.table(ReportType.SURVIVAL, null, null, true);

        var csv = new String(reportFileWriter.write(table, ReportFormat.CSV), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿\"Disciplină\",\"n\",").contains("\r\n");
        assertThat(reportFileWriter.write(table, ReportFormat.XLSX)).startsWith('P', 'K');
        assertThat(new String(reportFileWriter.write(reportService.table(ReportType.RADAR, null, null, true), ReportFormat.PDF),
                StandardCharsets.ISO_8859_1)).startsWith("%PDF");
    }

    @Test
    void reportRoleReadsOnlyTheReportViews() {
        var report = JdbcClient.create(reportDataSource);
        assertThat(report.sql("SELECT count(*) FROM report.prof_subject").query(Long.class).single()).isPositive();
        assertThatThrownBy(() -> report.sql("SELECT 1 FROM anon.rating LIMIT 1").query(Integer.class).list())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> report.sql("SELECT 1 FROM anon.prof_subject_radar LIMIT 1").query(Integer.class).list())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> report.sql("SELECT 1 FROM core.user_account LIMIT 1").query(Integer.class).list())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }
}
