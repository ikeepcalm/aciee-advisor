package ro.aciee.advisor.domain.leadership.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class Leadership {
    private List<LeaderClaim> claims;
    private List<Leader> leaders;
}
