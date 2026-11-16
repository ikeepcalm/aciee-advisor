package ro.aciee.advisor.domain.feedback.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FeedbackRequest {
    @NotBlank
    @Size(max = 2000)
    private String message;
    @Size(max = 300)
    private String page;
    @Email
    @Size(max = 254)
    private String replyTo;
}
