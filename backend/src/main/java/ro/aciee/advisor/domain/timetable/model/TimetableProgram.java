package ro.aciee.advisor.domain.timetable.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TimetableProgram {
    private String code;
    private int year;
    private String name;
    private String title;
    private String semester;
    private String pdfUrl;
    private Instant updatedAt;
    private List<String> groups;
    private List<Lesson> lessons;
}
