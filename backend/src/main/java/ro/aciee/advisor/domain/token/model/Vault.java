package ro.aciee.advisor.domain.token.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Vault {
    @NotNull
    @Size(max = 262144)
    private byte[] ciphertext;
    @NotNull
    @Size(min = 12, max = 12)
    private byte[] nonce;
}
