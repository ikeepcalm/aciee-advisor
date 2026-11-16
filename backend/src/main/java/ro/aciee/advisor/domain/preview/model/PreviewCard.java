package ro.aciee.advisor.domain.preview.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import ro.aciee.advisor.domain.preview.type.PreviewPage;

import java.util.List;

@Getter
@AllArgsConstructor
public class PreviewCard {
    private String eyebrow;
    private String title;
    private List<PreviewMetric> metrics;
    private String note;
    private String path;
    private String imagePath;
    private PreviewPage page;

    public String description() {
        if (metrics.isEmpty()) {
            return note;
        }
        var parts = metrics.stream().map(metric -> metric.getLabel() + ": " + metric.getValue()).toList();
        return String.join(" · ", parts) + " · " + note;
    }
}
