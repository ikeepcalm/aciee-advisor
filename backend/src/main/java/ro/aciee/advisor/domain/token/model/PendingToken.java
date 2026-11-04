package ro.aciee.advisor.domain.token.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PendingToken {
    private int offeringId;
    private String title;
    private byte[] fingerprint;
}
