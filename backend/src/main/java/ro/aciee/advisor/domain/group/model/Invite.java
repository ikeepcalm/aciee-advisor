package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class Invite {
    private int id;
    private OffsetDateTime expiresAt;
    private int maxUses;
    private long uses;
}
