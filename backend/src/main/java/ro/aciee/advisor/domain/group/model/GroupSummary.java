package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupSummary {
    private int id;
    private String code;
    private String specialization;
    private int studyYear;
    private String academicYear;
}
