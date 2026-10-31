package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeachingSlot {
    private int offeringId;
    private String title;
    private int semesterNo;
    private String component;
    private Integer subgroupId;
    private String subgroup;
    private Integer professorId;

    public String key() {
        return offeringId + "/" + component + "/" + subgroupId;
    }
}
