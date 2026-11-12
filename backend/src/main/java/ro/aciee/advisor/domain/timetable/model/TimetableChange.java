package ro.aciee.advisor.domain.timetable.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TimetableChange {
    private String change;
    private OffsetDateTime detectedAt;
    private int day;
    private String startTime;
    private String endTime;
    private String weeks;
    private String kind;
    private String subject;
    private String room;
    private String teacher;
}
