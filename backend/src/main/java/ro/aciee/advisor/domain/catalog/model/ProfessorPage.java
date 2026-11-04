package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProfessorPage {
    private int id;
    private String fullName;
    private String title;
    private String department;
    private boolean archived;
    private int threshold;
    private List<ProfessorSubject> subjects;
}
