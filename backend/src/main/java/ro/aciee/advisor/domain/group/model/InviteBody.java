package ro.aciee.advisor.domain.group.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InviteBody {
    @Min(1)
    @Max(30)
    private int days;
    @Min(1)
    @Max(200)
    private int maxUses;
}
