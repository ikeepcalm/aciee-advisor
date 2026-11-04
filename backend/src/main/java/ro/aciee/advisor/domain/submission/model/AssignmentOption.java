package ro.aciee.advisor.domain.submission.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AssignmentOption {
    private int id;
    private String component;
    private String componentLabel;
    private String professor;
    private String title;
    private String subgroup;
}
