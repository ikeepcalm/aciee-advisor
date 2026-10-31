package ro.aciee.advisor.domain.group.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class InvitePreview {
    private GroupSummary group;
    private List<Subgroup> subgroups;
}
