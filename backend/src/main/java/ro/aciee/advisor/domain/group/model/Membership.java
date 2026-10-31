package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class Membership {
    private int groupId;
    private String groupCode;
    private String specialization;
    private int studyYear;
    private String academicYear;
    private String subgroup;
    private LocalDate since;
    private LocalDate until;
    private boolean leader;
}
