package ro.aciee.advisor.domain.report.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.report.model.ReportFilters;
import ro.aciee.advisor.domain.report.model.ReportTable;
import ro.aciee.advisor.domain.report.model.source.ReportFormat;
import ro.aciee.advisor.domain.report.model.source.ReportType;
import ro.aciee.advisor.domain.report.service.ReportFileWriter;
import ro.aciee.advisor.domain.report.service.ReportService;

import java.util.Locale;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportFileWriter reportFileWriter;

    @GetMapping("/filters")
    public ReportFilters filters() {
        return reportService.filters();
    }

    @GetMapping("/{type}")
    public ReportTable preview(@PathVariable String type, @RequestParam(required = false) Integer specialization,
                               @RequestParam(required = false) Integer department, @RequestParam(defaultValue = "false") boolean locked) {
        return reportService.table(ReportType.of(type), specialization, department, locked);
    }

    @GetMapping("/{type}/{format}")
    public ResponseEntity<byte[]> download(@PathVariable String type, @PathVariable String format,
                                           @RequestParam(required = false) Integer specialization,
                                           @RequestParam(required = false) Integer department,
                                           @RequestParam(defaultValue = "false") boolean locked) {
        var reportType = ReportType.of(type);
        var reportFormat = ReportFormat.of(format);
        var file = reportFileWriter.write(reportService.table(reportType, specialization, department, locked), reportFormat);
        var name = "aciee-%s.%s".formatted(reportType.name(), reportFormat.name()).toLowerCase(Locale.ROOT);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, reportFormat.getMediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(file);
    }
}
