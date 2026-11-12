package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProfessorSubject {
    private int subjectId;
    private String subjectName;
    private int itemId;
    private String components;
    private int firstYear;
    private int lastYear;
    private Radar radar;
    private List<PublishedText> comments;
}
