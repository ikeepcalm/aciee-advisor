package ro.aciee.advisor.domain.submission.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PostState {
    private UUID id;
    private String kind;
    private Integer teachingAssignmentId;
    private String body;
    private String status;
    private String reason;
    private boolean published;
}
