package ro.aciee.advisor.domain.admin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminColumn {
    private String name;
    private String type;
    private String ref;
    private boolean nullable;
    private boolean readOnly;
    @JsonIgnore
    private String derivedFrom;

    public boolean isEditable() {
        return !readOnly && derivedFrom == null;
    }
}
