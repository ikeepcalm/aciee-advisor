package ro.aciee.advisor.domain.token.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class RateableOffering {
    private int offeringId;
    private String title;
    private String academicYear;
    private int semesterNo;
    private OffsetDateTime opensAt;
    private boolean open;
    private byte[] fingerprint;
    private boolean issued;

    public boolean pending() {
        return open && fingerprint != null && !issued;
    }
}
