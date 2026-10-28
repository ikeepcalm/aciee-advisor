package ro.aciee.advisor.domain.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResendCodeRequest {
    @NotBlank
    @Size(max = 254)
    private String email;
}
