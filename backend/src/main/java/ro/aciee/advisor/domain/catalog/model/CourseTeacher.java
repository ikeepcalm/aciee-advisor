package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CourseTeacher {
    private int professorId;
    private String slug;
    private String fullName;
    private String title;
    private String component;
    private String componentLabel;
    private String subgroups;
    private Radar radar;
    private List<PublishedText> comments;
}
