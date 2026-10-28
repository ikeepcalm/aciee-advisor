package ro.aciee.advisor.domain.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignUpRequest {
    @NotBlank
    @Email
    @Size(max = 254)
    @Pattern(regexp = "[^@\\s]+@student\\.ugal\\.ro", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String email;
    @Size(min = 10, max = 128)
    @NotBlank
    private String password;
}
