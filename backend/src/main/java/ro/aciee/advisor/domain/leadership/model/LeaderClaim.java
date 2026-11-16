package ro.aciee.advisor.domain.leadership.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class LeaderClaim {
    private int id;
    private String email;
    private String displayName;
    private String groupCode;
    private String academicYear;
    private LocalDate memberSince;
}
