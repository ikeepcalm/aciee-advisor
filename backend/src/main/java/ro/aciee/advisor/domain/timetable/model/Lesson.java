package ro.aciee.advisor.domain.timetable.model;

import lombok.Getter;
import lombok.Setter;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;

import java.util.List;

@Getter
public class Lesson {
    private int day;
    private String start;
    private String end;
    private WeekParity weeks;
    private List<String> groups;
    private String kind;
    private String subject;
    private String room;
    private String teacher;
    private String note;
    private List<String> lines;
    @Setter
    private String course;
    @Setter
    private Integer courseId;

    public Lesson(int day, String start, String end, WeekParity weeks, List<String> groups, String kind, String subject,
                  String room, String teacher, String note, List<String> lines) {
        this.day = day;
        this.start = start;
        this.end = end;
        this.weeks = weeks;
        this.groups = groups;
        this.kind = kind;
        this.subject = subject;
        this.room = room;
        this.teacher = teacher;
        this.note = note;
        this.lines = lines;
        this.course = subject;
    }
}
