package ro.aciee.advisor.domain.timetable.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CourseTitles {

    private static final Set<String> FILLER = Set.of("de", "pe", "si", "in", "la", "cu", "a", "al", "ale", "din", "pentru",
            "anul", "limba", "i", "ii", "iii", "iv", "v", "vi", "1", "2", "3", "4");
    private static final Map<String, String> NUMERALS = Map.of("i", "1", "ii", "2", "iii", "3", "iv", "4", "1", "1", "2", "2", "3", "3",
            "4", "4");
    private static final Pattern ACRONYM = Pattern.compile("([a-z]{2,5})([1-4]?)");

    private CourseTitles() {
    }

    public static int match(String course, List<String> titles) {
        var wanted = words(course);
        String acronym = acronym(course);
        String numeral = numeral(course);
        int best = -1;
        double bestScore = 0;
        double second = 0;
        for (int i = 0; i < titles.size(); i++) {
            double score = similarity(wanted, words(titles.get(i)));
            if (acronym != null && acronym.equals(initials(titles.get(i)))) {
                score = Math.max(score, 0.9);
            }
            // "Calculatoare numerice I" ties with "... II" on words; the numeral breaks the tie
            if (numeral != null && numeral.equals(numeral(titles.get(i)))) {
                score += 0.01;
            }
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = i;
            } else if (score > second) {
                second = score;
            }
        }
        return bestScore >= 0.6 && bestScore > second ? best : -1;
    }

    // "PCLP II", "IA2", "POO": one short word, optionally followed by a numeral
    private static String acronym(String course) {
        var tokens = Arrays.stream(LessonNames.fold(course).split("[^a-z0-9]+")).filter(t -> !t.isEmpty()).toList();
        if (tokens.size() == 2 && NUMERALS.containsKey(tokens.get(1))) {
            tokens = tokens.subList(0, 1);
        }
        if (tokens.size() != 1) {
            return null;
        }
        var match = ACRONYM.matcher(tokens.getFirst());
        return match.matches() ? match.group(1) : null;
    }

    private static String numeral(String title) {
        var tokens = LessonNames.fold(title).split("[^a-z0-9]+");
        if (tokens.length == 0) {
            return null;
        }
        String last = tokens[tokens.length - 1];
        var glued = ACRONYM.matcher(last);
        return NUMERALS.getOrDefault(last, glued.matches() && !glued.group(2).isEmpty() ? glued.group(2) : null);
    }

    private static String initials(String title) {
        return Arrays.stream(LessonNames.fold(title).split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty() && !FILLER.contains(word) && !Character.isDigit(word.charAt(0)))
                .map(word -> word.substring(0, 1))
                .collect(Collectors.joining());
    }

    static Set<String> words(String title) {
        String folded = LessonNames.fold(title).replaceAll("\\blb\\.", "limba ");
        return Arrays.stream(folded.split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty() && !FILLER.contains(word))
                .collect(Collectors.toSet());
    }

    private static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        long common = a.stream().filter(b::contains).count();
        return (double) common / (a.size() + b.size() - common);
    }
}
