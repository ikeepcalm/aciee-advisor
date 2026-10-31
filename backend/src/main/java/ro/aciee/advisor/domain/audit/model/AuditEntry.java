package ro.aciee.advisor.domain.audit.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {
    private long id;
    private OffsetDateTime createdAt;
    private String actor;
    private String tableName;
    private int rowId;
    private String op;
    private String old;
    private String current;
}
