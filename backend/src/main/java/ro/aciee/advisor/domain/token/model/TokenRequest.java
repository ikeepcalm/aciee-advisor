package ro.aciee.advisor.domain.token.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TokenRequest {
    @NotNull
    private Integer offeringId;
    @NotNull
    private byte[] blindedMsg;
}
