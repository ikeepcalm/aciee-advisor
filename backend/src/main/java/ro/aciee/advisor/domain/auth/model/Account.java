package ro.aciee.advisor.domain.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class Account {
    private int id;
    private String email;
    @JsonIgnore
    private String passwordHash;
    @JsonIgnore
    private boolean disabled;
    @JsonIgnore
    private boolean unverified;
    private List<String> roles;
}
