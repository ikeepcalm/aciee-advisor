package ro.aciee.advisor.domain.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignInRequest {
    @NotBlank
    @Size(max = 254)
    private String email;
    @NotBlank
    @Size(max = 128)
    private String password;
}
