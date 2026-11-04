package ro.aciee.advisor.domain.moderation.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DecisionBody {
    @NotBlank
    private String reasonCode;
}
