package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Survival {
    private int n;
    private Double examDifficulty;
    private Double labsHelp;
    private Double attendanceStrictness;
    private Double weeklyHours;
    private String attendanceUseful;
    private String examForm;
    private String examContent;
}
