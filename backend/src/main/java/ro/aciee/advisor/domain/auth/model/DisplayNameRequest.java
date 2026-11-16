package ro.aciee.advisor.domain.auth.model;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DisplayNameRequest {
    @Size(max = 200)
    private String name;
}
