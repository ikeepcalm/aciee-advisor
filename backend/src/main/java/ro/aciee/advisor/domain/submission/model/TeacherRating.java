package ro.aciee.advisor.domain.submission.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRating {
    @NotNull
    private Integer teachingAssignmentId;
    @NotNull
    @Size(min = 24, max = 24)
    private List<@Min(0) @Max(10) Integer> scores;
    @Size(max = 280)
    private String comment;

    public boolean scored() {
        return scores.stream().anyMatch(Objects::nonNull);
    }
}
