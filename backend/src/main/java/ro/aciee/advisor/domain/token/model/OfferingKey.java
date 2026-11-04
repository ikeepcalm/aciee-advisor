package ro.aciee.advisor.domain.token.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OfferingKey {
    private int offeringId;
    private byte[] fingerprint;
    private byte[] publicKey;
}
