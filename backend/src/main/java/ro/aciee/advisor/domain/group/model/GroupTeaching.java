package ro.aciee.advisor.domain.group.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GroupTeaching {
    private List<TeachingSlot> slots;
    private List<ProfessorOption> professors;
}
