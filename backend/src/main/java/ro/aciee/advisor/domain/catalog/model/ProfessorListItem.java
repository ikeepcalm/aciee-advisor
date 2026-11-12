package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProfessorListItem {
    private String slug;
    private String fullName;
    private String title;
    private String department;
    private int subjects;
    private int ratings;
    private Double overall;
    private String photo;
}
