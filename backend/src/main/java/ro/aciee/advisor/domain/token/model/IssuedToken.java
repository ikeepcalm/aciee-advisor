package ro.aciee.advisor.domain.token.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IssuedToken {
    private int offeringId;
    private byte[] blindedMsg;
    private byte[] blindSig;
}
