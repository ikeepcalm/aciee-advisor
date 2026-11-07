package ro.aciee.advisor.domain.report.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportSection {
    private String title;
    private int fromColumn;
    private int toColumn;
}
