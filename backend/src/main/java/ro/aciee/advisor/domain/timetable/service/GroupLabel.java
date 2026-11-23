package ro.aciee.advisor.domain.timetable.service;

import java.util.regex.Pattern;

public record GroupLabel(String group, String subgroup) {

    private static final Pattern SUBGROUP = Pattern.compile("^(.*\\d)([A-Z])$");
    private static final String CYRILLIC = "АВСЕНІКМОРТХУ";
    private static final String LATIN = "ABCEHIKMOPTXY";

    public static GroupLabel of(String label) {
        var latin = new StringBuilder();
        for (char c : label.trim().toUpperCase().toCharArray()) {
            int i = CYRILLIC.indexOf(c);
            latin.append(i < 0 ? c : LATIN.charAt(i));
        }
        var match = SUBGROUP.matcher(latin);
        return match.matches() ? new GroupLabel(match.group(1), match.group(2)) : new GroupLabel(latin.toString(), null);
    }
}
