package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JoinRequest {
    private int id;
    private String email;
    private String groupCode;
    private String academicYear;
    private String kind;
    private String subgroup;
    private String status;
}
