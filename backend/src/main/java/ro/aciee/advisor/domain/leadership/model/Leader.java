package ro.aciee.advisor.domain.leadership.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Leader {
    private int membershipId;
    private String email;
    private String groupCode;
    private String academicYear;
}
