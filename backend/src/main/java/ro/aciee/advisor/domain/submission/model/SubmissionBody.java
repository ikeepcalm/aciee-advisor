package ro.aciee.advisor.domain.submission.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SubmissionBody {
    private Integer offeringId;
    private byte[] fingerprint;
    private byte[] msg;
    private byte[] sig;
    @Min(0)
    @Max(10)
    private Integer examDifficulty;
    @Min(0)
    @Max(10)
    private Integer labsHelp;
    @Min(0)
    @Max(10)
    private Integer attendanceStrictness;
    private String attendanceUseful;
    @Size(max = 7)
    private List<String> exam;
    @Min(0)
    @Max(80)
    private Integer weeklyHours;
    @NotNull
    private List<@Valid @NotNull TeacherRating> ratings;
    @Size(max = 1000)
    private String tip;
}
