package ro.aciee.advisor.domain.moderation.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class QueueItem {
    private UUID postId;
    private int versionNo;
    private String kind;
    private String body;
    private LocalDate week;
    private String course;
    private String previousBody;
}
