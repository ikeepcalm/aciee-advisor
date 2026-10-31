package ro.aciee.advisor.domain.group.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MyGroups {
    private List<Membership> memberships;
    private List<JoinRequest> requests;
}
