package ro.aciee.advisor.domain.timetable.service;

import java.util.regex.Pattern;

public record GroupLabel(String group, String subgroup) {

    private static final Pattern SUBGROUP = Pattern.compile("^(.*\\d)([A-Z])$");

    public static GroupLabel of(String label) {
        var match = SUBGROUP.matcher(label.trim());
        return match.matches() ? new GroupLabel(match.group(1), match.group(2)) : new GroupLabel(label.trim(), null);
    }
}
