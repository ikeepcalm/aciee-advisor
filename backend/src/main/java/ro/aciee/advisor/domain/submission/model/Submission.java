package ro.aciee.advisor.domain.submission.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Submission {
    private int offeringId;
    private Integer examDifficulty;
    private Integer labsHelp;
    private Integer attendanceStrictness;
    private String attendanceUseful;
    private List<String> exam;
    private Integer weeklyHours;
    private List<TeacherRating> ratings;
    private List<PostState> posts;
}
