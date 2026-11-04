package ro.aciee.advisor.domain.submission.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class OfferingForm {
    private int offeringId;
    private String title;
    private String academicYear;
    private int semesterNo;
    private List<AssignmentOption> assignments;
}
