package ro.aciee.advisor.domain.group.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class CreatedInvite {
    private int id;
    private String token;
    private OffsetDateTime expiresAt;
}
