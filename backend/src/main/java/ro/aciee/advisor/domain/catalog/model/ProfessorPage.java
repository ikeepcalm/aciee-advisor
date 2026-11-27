package ro.aciee.advisor.domain.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProfessorPage {
    private int id;
    private String fullName;
    private String title;
    private String department;
    private boolean archived;
    private int threshold;
    private int ratings;
    private Double overall;
    private Double lecture;
    private Double lab;
    private Double seminar;
    private Double project;
    private Double grading;
    private Radar radar;
    private String bioRo;
    private String bioEn;
    private String photo;
    private String cvUrl;
    private List<ProfessorSubject> subjects;
}
