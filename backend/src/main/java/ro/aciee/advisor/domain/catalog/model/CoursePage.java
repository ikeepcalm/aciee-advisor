package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CoursePage {
    private int itemId;
    private int rootSubjectId;
    private String subjectName;
    private String specialization;
    private int studyYear;
    private int semesterNo;
    private int planId;
    private int threshold;
    private Integer academicYearId;
    private List<YearOption> years;
    private List<CourseTeacher> teachers;
    private Survival survival;
    private List<PublishedText> tips;
    private List<Integer> offeringIds;
}
