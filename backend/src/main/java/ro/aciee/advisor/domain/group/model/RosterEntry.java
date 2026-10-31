package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class RosterEntry {
    private String email;
    private String subgroup;
    private LocalDate since;
    private boolean leader;
}
