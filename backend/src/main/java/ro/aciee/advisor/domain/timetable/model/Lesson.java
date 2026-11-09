package ro.aciee.advisor.domain.timetable.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;

import java.util.List;

@Getter
@AllArgsConstructor
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
}
