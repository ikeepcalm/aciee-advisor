package ro.aciee.advisor.domain.timetable.service;

import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LessonNames {

    private static final Pattern NAMED_ABBREVIATION = Pattern.compile("^(.+?)\\s*\\(([^()]+)\\)\\s*$");
    private static final Pattern SEMESTER = Pattern.compile("Semestrul\\s+(II|I)\\b");

    private LessonNames() {
    }

    public static void attachCourseNames(TimetableProgram program) {
        var fullNames = new HashMap<String, String>();
        for (Lesson lesson : program.getLessons()) {
            var match = NAMED_ABBREVIATION.matcher(lesson.getSubject());
            if (match.matches()) {
                fullNames.putIfAbsent(match.group(2).trim(), match.group(1).trim());
            }
        }
        for (Lesson lesson : program.getLessons()) {
            var match = NAMED_ABBREVIATION.matcher(lesson.getSubject());
            String subject = lesson.getSubject().trim();
            lesson.setCourse(match.matches() ? match.group(1).trim() : fullNames.getOrDefault(subject, subject));
        }
    }

    public static int semesterNo(TimetableProgram program) {
        var match = SEMESTER.matcher(program.getSemester() == null ? "" : program.getSemester());
        return match.find() && match.group(1).equals("II") ? 2 : 1;
    }

    public static String fold(String text) {
        String stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
