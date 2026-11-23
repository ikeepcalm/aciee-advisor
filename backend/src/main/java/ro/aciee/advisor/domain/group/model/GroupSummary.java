package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class GroupSummary {
    private int id;
    private String code;
    private String specialization;
    private int studyYear;
    private String academicYear;
    private List<Subgroup> subgroups = List.of();
    private boolean hasLeader;
}
