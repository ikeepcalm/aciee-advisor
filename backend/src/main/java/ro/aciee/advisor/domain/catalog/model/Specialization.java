package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Specialization {
    private int id;
    private String code;
    private String name;
    private String degree;
    private int durationYears;
    private List<PlanOption> plans;
}
