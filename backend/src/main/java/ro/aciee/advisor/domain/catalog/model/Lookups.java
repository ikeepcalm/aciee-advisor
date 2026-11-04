package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Lookups {
    private List<Option> axes;
    private List<Option> pillars;
    private List<Option> examFormats;
    private List<Option> attendance;
    private List<Option> reportReasons;
    private List<Option> moderationReasons;
    private int threshold;
}
