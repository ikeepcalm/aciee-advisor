package ro.aciee.advisor.domain.report.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ro.aciee.advisor.domain.catalog.model.Option;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReportFilters {
    private List<Option> specializations;
    private List<Option> departments;
}
