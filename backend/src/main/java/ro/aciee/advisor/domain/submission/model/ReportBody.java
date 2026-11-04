package ro.aciee.advisor.domain.submission.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReportBody {
    @NotBlank
    private String reasonCode;
}
