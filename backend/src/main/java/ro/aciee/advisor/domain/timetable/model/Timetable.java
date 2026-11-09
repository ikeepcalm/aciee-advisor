package ro.aciee.advisor.domain.timetable.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class Timetable {
    private Instant checkedAt;
    private String source;
    private List<TimetableProgram> programs;
}
