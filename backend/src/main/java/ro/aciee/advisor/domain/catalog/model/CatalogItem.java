package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogItem {
    private int id;
    private String subjectName;
    private int semesterNo;
    private Integer electiveSlot;
    private int lectureHours;
    private int seminarHours;
    private int labHours;
    private int projectHours;
}
