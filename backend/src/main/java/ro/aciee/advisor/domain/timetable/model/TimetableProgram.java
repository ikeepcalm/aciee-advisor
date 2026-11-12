package ro.aciee.advisor.domain.timetable.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import ro.aciee.advisor.domain.timetable.model.source.WeekParity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    private Map<LocalDate, WeekParity> weeks = Map.of();
}
