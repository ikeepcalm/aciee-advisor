package ro.aciee.advisor.domain.group.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JoinBody {
    @NotBlank
    @Size(max = 64)
    private String token;
    private Integer subgroupId;
}
