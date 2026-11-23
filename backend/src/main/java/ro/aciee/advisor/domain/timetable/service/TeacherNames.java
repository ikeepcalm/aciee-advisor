package ro.aciee.advisor.domain.timetable.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TeacherNames {

    private static final Pattern TITLE = Pattern.compile(
            "^\\s*(prof|conf|[sș]\\.?\\s?l|lector|lect|asist|asis|as|drd|dr|ing|ec|fiz|inf)(?=[\\s.]|$)[\\s.]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SPLIT = Pattern.compile("[\\s,/]+");

    public record Teacher(String raw, String title, List<String> words, List<Character> initials) {

        Set<String> folded() {
            return words.stream().map(TeacherNames::foldWord).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        boolean full() {
            return words.size() > 1;
        }

        Set<String> keys() {
            var keys = new LinkedHashSet<String>();
            var folded = List.copyOf(folded());
            var surnames = full() ? List.of(folded.getFirst(), folded.getLast()) : folded;
            for (String surname : surnames) {
                for (String other : folded) {
                    if (!other.equals(surname)) {
                        keys.add(surname + "|" + other.charAt(0));
                    }
                }
                for (char initial : initials) {
                    keys.add(surname + "|" + foldWord(String.valueOf(initial)));
                }
            }
            return keys;
        }

        String surnameOnly() {
            return words.size() == 1 && initials.isEmpty() ? foldWord(words.getFirst()) : null;
        }

        String displayName() {
            if (full() || initials.isEmpty()) {
                return String.join(" ", words);
            }
            return initials.stream().map(c -> c + ".").collect(Collectors.joining()) + " " + words.getFirst();
        }
    }

    public record Cluster(List<Teacher> variants) {

        public String displayName() {
            return variants.stream()
                    .max(Comparator.<Teacher>comparingInt(v -> v.words().size())
                            .thenComparingInt(v -> v.initials().size())
                            .thenComparingLong(v -> v.displayName().chars().filter(c -> c > 127).count())
                            .thenComparingLong(v -> variants.stream().filter(o -> o.displayName().equals(v.displayName())).count())
                            .thenComparingInt(v -> v.displayName().length()))
                    .map(Teacher::displayName)
                    .orElseThrow();
        }

        public String title() {
            return variants.stream().map(Teacher::title).filter(t -> t != null)
                    .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("cadru");
        }

        public boolean matches(String fullName) {
            var other = parse(fullName);
            return variants.stream().anyMatch(v -> same(v, other));
        }
    }

    private TeacherNames() {
    }

    public static Teacher parse(String raw) {
        String rest = raw;
        var titles = new ArrayList<String>();
        var match = TITLE.matcher(rest);
        while (match.find()) {
            titles.add(foldWord(match.group(1)));
            rest = rest.substring(match.end());
            match = TITLE.matcher(rest);
        }
        var words = new ArrayList<String>();
        var initials = new ArrayList<Character>();
        for (String token : SPLIT.split(rest.replace(".", ". ").trim())) {
            String letters = token.replaceAll("[^\\p{L}-]", "").replaceAll("^-+|-+$", "");
            if (letters.isEmpty()) {
                continue;
            }
            if (letters.length() == 1 || (token.endsWith(".") && letters.length() <= 3)) {
                initials.add(Character.toUpperCase(letters.charAt(0)));
            } else {
                words.add(letters);
            }
        }
        return new Teacher(raw, title(titles), List.copyOf(words), List.copyOf(initials));
    }

    public static List<Cluster> cluster(Collection<String> raws) {
        var teachers = raws.stream().distinct().map(TeacherNames::parse).filter(t -> !t.words().isEmpty()).toList();
        var parent = new int[teachers.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < teachers.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (same(teachers.get(i), teachers.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        for (int i = 0; i < teachers.size(); i++) {
            String surname = teachers.get(i).surnameOnly();
            if (surname == null) {
                continue;
            }
            int self = i;
            var roots = new LinkedHashSet<Integer>();
            for (int j = 0; j < teachers.size(); j++) {
                if (j != self && teachers.get(j).surnameOnly() == null && teachers.get(j).folded().contains(surname)) {
                    roots.add(find(parent, j));
                }
            }
            if (roots.size() == 1) {
                union(parent, i, roots.getFirst());
            }
        }
        var groups = new LinkedHashMap<Integer, List<Teacher>>();
        for (int i = 0; i < teachers.size(); i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(teachers.get(i));
        }
        return groups.values().stream().map(Cluster::new).toList();
    }

    static boolean same(Teacher a, Teacher b) {
        if (a.words().isEmpty() || b.words().isEmpty() || a.surnameOnly() != null || b.surnameOnly() != null) {
            return false;
        }
        if (a.full() && b.full()) {
            return a.folded().containsAll(b.folded()) || b.folded().containsAll(a.folded());
        }
        if (!a.full() && !b.full()) {
            return a.initials().getFirst() == b.initials().getFirst()
                    && close(foldWord(a.words().getFirst()), foldWord(b.words().getFirst()));
        }
        var shorter = a.full() ? b : a;
        var longer = a.full() ? a : b;
        return shorter.keys().stream().anyMatch(longer.keys()::contains);
    }

    static String foldWord(String word) {
        return LessonNames.fold(word).replaceAll("[^a-z]", "");
    }

    private static boolean close(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        int allowed = Math.min(a.length(), b.length()) >= 9 ? 2 : Math.min(a.length(), b.length()) >= 5 ? 1 : 0;
        return allowed > 0 && distance(a, b) <= allowed;
    }

    private static int distance(String a, String b) {
        var previous = new int[b.length() + 1];
        var current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            var swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String title(List<String> titles) {
        if (titles.isEmpty()) {
            return null;
        }
        return switch (titles.getFirst()) {
            case "prof" -> titles.contains("dr") ? "prof" : null;
            case "conf" -> "conf";
            case "sl" -> "sl";
            case "lect", "lector" -> "lect";
            case "as", "asis", "asist", "drd" -> "asist";
            default -> null;
        };
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        parent[find(parent, a)] = find(parent, b);
    }
}
