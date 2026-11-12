package ro.aciee.advisor.domain.preview.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreviewMetric {
    private String label;
    private String value;
    private boolean headline;
}
