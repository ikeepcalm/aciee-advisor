package ro.aciee.advisor.domain.auth.model;

import lombok.Data;

@Data
public class VerificationCodeIssued {
    private final String email;
    private final String code;
}
