package ro.aciee.advisor.domain.report.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReportTable {
    private String title;
    private int keyColumns;
    private List<String> columns;
    private List<ReportSection> sections;
    private List<List<Object>> rows;
}
