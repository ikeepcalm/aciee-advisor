package ro.aciee.advisor.domain.timetable.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CourseTitles {

    private static final Set<String> FILLER = Set.of("de", "pe", "si", "in", "la", "cu", "a", "al", "ale", "din", "pentru",
            "anul", "i", "ii", "iii", "iv", "v", "vi", "1", "2", "3", "4");

    private CourseTitles() {
    }

    public static int match(String course, List<String> titles) {
        var wanted = words(course);
        int best = -1;
        double bestScore = 0;
        double second = 0;
        for (int i = 0; i < titles.size(); i++) {
            double score = similarity(wanted, words(titles.get(i)));
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
