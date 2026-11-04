package ro.aciee.advisor.domain.moderation.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ReportedPost {
    private UUID postId;
    private String kind;
    private String course;
    private String body;
    private String reasons;
    private int count;
    private LocalDate week;
}
