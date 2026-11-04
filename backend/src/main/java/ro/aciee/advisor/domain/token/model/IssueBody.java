package ro.aciee.advisor.domain.token.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class IssueBody {
    @NotNull
    private List<@Valid @NotNull TokenRequest> tokens;
}
