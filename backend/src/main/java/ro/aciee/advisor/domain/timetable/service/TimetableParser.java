package ro.aciee.advisor.domain.timetable.service;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TimetableParser {

    private static final Pattern CLOCK = Pattern.compile("\\d\\d:\\d\\d");
    private static final Pattern GROUP = Pattern.compile("[A-Z0-9]{2,3}\\d\\d");
    private static final Pattern SUBGROUP = Pattern.compile("[a-z]");
    private static final Pattern KIND = Pattern.compile("\\(\\s*([CSLP])\\s*\\)");

    private final List<Word> words;
    private final List<Box> horizontals = new ArrayList<>();
    private final List<Box> verticals = new ArrayList<>();
    private final List<Box> slants = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final List<Column> columns = new ArrayList<>();
    private int[] parent;

    private TimetableParser(List<Word> words, List<Box> boxes) {
        this.words = words;
        for (var box : boxes) {
            if (!box.isDark()) {
                continue;
            }
            if (box.isDiagonal()) {
                if (box.width() < 30 && box.height() > 5) {
                    slants.add(box);
                }
            } else if (box.width() > 3 && box.height() < 2) {
                horizontals.add(box);
            } else if (box.height() > 3 && box.width() < 2) {
                verticals.add(box);
            }
        }
    }

    public static TimetableProgram parse(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            var parser = new TimetableParser(WordCollector.collect(document), new BoxCollector(document.getPage(0)).collect());
            return parser.read();
        }
    }

    private TimetableProgram read() {
        findSlots();
        findColumns();
        if (columns.isEmpty()) {
            throw new IllegalStateException("no timetable columns found");
        }
        mergeCells();
        var program = new TimetableProgram();
        program.setTitle(title());
        program.setSemester(lineContaining("Semestrul"));
        program.setGroups(columns.stream().map(Column::getLabel).toList());
        program.setLessons(lessons());
        return program;
    }

    private void findSlots() {
        var clocks = words.stream().filter(w -> CLOCK.matcher(w.getText()).matches()).toList();
        var labels = new LinkedHashMap<Word, Word>();
        for (var start : clocks) {
            clocks.stream()
                    .filter(w -> Math.abs(w.getTop() - start.getTop()) < 2 && w.getX0() > start.getX1() && w.getX0() - start.getX1() < 30)
                    .min(Comparator.comparing(Word::getX0))
                    .ifPresent(end -> labels.put(start, end));
        }
        var timeColumn = labels.keySet().stream().map(Word::getX0).min(Float::compare).orElseThrow(() -> new IllegalStateException("no time column"));
        var fullLines = horizontals.stream().filter(h -> h.width() > 200).map(Box::getTop).sorted().toList();
        int day = -1;
        String previous = null;
        for (var start : labels.keySet().stream().filter(w -> w.getX0() - timeColumn < 15).sorted(Comparator.comparing(Word::getTop)).toList()) {
            var end = labels.get(start);
            if (previous == null || start.getText().compareTo(previous) <= 0) {
                day++;
            }
            previous = start.getText();
            var y = start.centerY();
            var top = fullLines.stream().filter(l -> l < y - 3).reduce((a, b) -> b).orElseThrow();
            var bottom = fullLines.stream().filter(l -> l > y + 3).findFirst().orElseThrow();
            slots.add(new Slot(day + 1, start.getText(), end.getText(), top, bottom, end.getX1()));
        }
        if (slots.isEmpty()) {
            throw new IllegalStateException("no time slots found");
        }
    }

    private void findColumns() {
        var timeRight = slots.stream().map(Slot::getTimeRight).max(Float::compare).orElseThrow();
        var gridTop = slots.getFirst().getTop();
        var tableRight = verticals.stream().map(Box::getX0).max(Float::compare).orElseThrow();
        var left = verticals.stream().filter(v -> v.getX0() > timeRight && v.getBottom() > gridTop + 10)
                .map(Box::getX0).min(Float::compare).orElseThrow();
        var bounds = new TreeSet<Integer>();
        bounds.add(Math.round(left));
        bounds.add(Math.round(tableRight));
        verticals.stream()
                .filter(v -> v.getTop() < gridTop - 3 && v.getBottom() >= gridTop - 1 && v.getX0() > left + 5)
                .forEach(v -> bounds.add(Math.round(v.getX0())));
        var header = words.stream().filter(w -> w.getBottom() < gridTop + 4 && w.getX0() > left - 2).toList();
        var codes = header.stream().filter(w -> GROUP.matcher(w.getText()).matches()).toList();
        Integer previous = null;
        for (var x : bounds) {
            if (previous != null && x - previous >= 10) {
                columns.add(column(previous, x, header, codes));
            }
            previous = x;
        }
    }

    private Column column(float x0, float x1, List<Word> header, List<Word> codes) {
        var center = (x0 + x1) / 2;
        var group = codes.stream().min(Comparator.comparing(w -> Math.abs(w.centerX() - center))).map(Word::getText).orElse("");
        var subgroup = header.stream()
                .filter(w -> w.centerX() > x0 && w.centerX() < x1 && SUBGROUP.matcher(w.getText()).matches())
                .max(Comparator.comparing(Word::getTop)).map(Word::getText).orElse("");
        return new Column(x0, x1, group + subgroup);
    }

    private void mergeCells() {
        parent = IntStream.range(0, slots.size() * 2 * columns.size()).toArray();
        for (int s = 0; s < slots.size(); s++) {
            var slot = slots.get(s);
            for (int c = 0; c < columns.size(); c++) {
                if (!splitsWeeks(slot, columns.get(c))) {
                    union(cell(s, 0, c), cell(s, 1, c));
                }
                for (int half = 0; half < 2 && c + 1 < columns.size(); half++) {
                    var y = half == 0 ? (slot.getTop() + slot.middle()) / 2 : (slot.middle() + slot.getBottom()) / 2;
                    if (!hasWall(columns.get(c).getX1(), y)) {
                        union(cell(s, half, c), cell(s, half, c + 1));
                    }
                }
            }
        }
    }

    private boolean splitsWeeks(Slot slot, Column column) {
        var width = column.getX1() - column.getX0();
        return horizontals.stream().anyMatch(h -> Math.abs(h.getTop() - slot.middle()) < 3
                && Math.min(h.getX1(), column.getX1()) - Math.max(h.getX0(), column.getX0()) > 0.4f * width);
    }

    private boolean hasWall(float x, float y) {
        return verticals.stream().anyMatch(v -> Math.abs(v.getX0() - x) < 3 && v.getTop() <= y && y <= v.getBottom())
                || slants.stream().anyMatch(d -> Math.abs(d.centerX() - x) < 14 && d.getTop() - 1 <= y && y <= d.getBottom() + 1);
    }

    private List<Lesson> lessons() {
        var regions = new LinkedHashMap<Integer, List<Word>>();
        for (var word : words) {
            for (int s = 0; s < slots.size(); s++) {
                var slot = slots.get(s);
                if (word.centerY() <= slot.getTop() || word.centerY() >= slot.getBottom()) {
                    continue;
                }
                for (int c = 0; c < columns.size(); c++) {
                    var column = columns.get(c);
                    if (word.centerX() > column.getX0() && word.centerX() < column.getX1()) {
                        var half = word.centerY() < slot.middle() ? 0 : 1;
                        regions.computeIfAbsent(find(cell(s, half, c)), k -> new ArrayList<>()).add(word);
                    }
                }
            }
        }
        var cellsByRoot = IntStream.range(0, parent.length).boxed().collect(Collectors.groupingBy(this::find));
        return regions.entrySet().stream()
                .map(region -> lesson(cellsByRoot.get(region.getKey()), region.getValue()))
                .sorted(Comparator.comparing(Lesson::getDay).thenComparing(Lesson::getStart).thenComparing(l -> l.getGroups().getFirst()))
                .toList();
    }

    private Lesson lesson(List<Integer> cells, List<Word> regionWords) {
        var slot = slots.get(cells.getFirst() / (2 * columns.size()));
        var halves = cells.stream().map(i -> (i / columns.size()) % 2).distinct().toList();
        var weeks = halves.size() == 2 ? WeekParity.ALL : halves.getFirst() == 0 ? WeekParity.ODD : WeekParity.EVEN;
        var groups = cells.stream().map(i -> i % columns.size()).distinct().sorted().map(c -> columns.get(c).getLabel()).toList();
        var lines = lines(regionWords);
        Matcher kind = KIND.matcher(String.join(" ", lines));
        var title = KIND.matcher(lines.getFirst()).replaceFirst("").replaceAll("\\s+", " ").strip();
        return new Lesson(slot.getDay(), slot.getStart(), slot.getEnd(), weeks, groups, kind.find() ? kind.group(1) : null, title, lines.subList(1, lines.size()));
    }

    private String title() {
        var tableTop = verticals.stream().filter(v -> v.height() > 100).map(Box::getTop).min(Float::compare).orElseThrow();
        return words.stream().filter(w -> w.getText().equals("ANUL")).findFirst()
                .map(anchor -> String.join(" ", lines(words.stream().filter(w -> Math.abs(w.getTop() - anchor.getTop()) < 2.5f || w.getTop() > anchor.getTop() && w.getBottom() < tableTop).toList())))
                .orElse("");
    }

    private String lineContaining(String token) {
        return words.stream().filter(w -> w.getText().equals(token)).findFirst()
                .map(anchor -> lines(words.stream().filter(w -> Math.abs(w.getTop() - anchor.getTop()) < 2.5f).toList()).getFirst())
                .orElse("");
    }

    private static List<String> lines(List<Word> lineWords) {
        var rows = new TreeMap<Float, List<Word>>();
        for (var word : lineWords) {
            var key = rows.keySet().stream().filter(top -> Math.abs(top - word.getTop()) < 2.5f).findFirst().orElse(word.getTop());
            rows.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
        }
        return rows.values().stream()
                .map(row -> row.stream().sorted(Comparator.comparing(Word::getX0)).map(Word::getText).collect(Collectors.joining(" ")))
                .toList();
    }

    private int cell(int slot, int half, int column) {
        return (slot * 2 + half) * columns.size() + column;
    }

    private int find(int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int a, int b) {
        parent[find(a)] = find(b);
    }

    @Data
    private static class Word {
        private final String text;
        private final float x0;
        private final float x1;
        private final float top;
        private final float bottom;

        float centerX() {
            return (x0 + x1) / 2;
        }

        float centerY() {
            return (top + bottom) / 2;
        }
    }

    @Data
    private static class Box {
        private final float x0;
        private final float x1;
        private final float top;
        private final float bottom;
        private final boolean dark;
        private final boolean diagonal;

        float width() {
            return x1 - x0;
        }

        float height() {
            return bottom - top;
        }

        float centerX() {
            return (x0 + x1) / 2;
        }
    }

    @Data
    private static class Slot {
        private final int day;
        private final String start;
        private final String end;
        private final float top;
        private final float bottom;
        private final float timeRight;

        float middle() {
            return (top + bottom) / 2;
        }
    }

    @Data
    private static class Column {
        private final float x0;
        private final float x1;
        private final String label;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class Subpath {
        private final List<Point2D> points;
        private boolean diagonal;
    }

    private static class WordCollector extends PDFTextStripper {

        private final List<Word> words = new ArrayList<>();

        static List<Word> collect(PDDocument document) throws IOException {
            var collector = new WordCollector();
            collector.setSortByPosition(true);
            collector.setEndPage(1);
            collector.getText(document);
            return collector.words;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            var chunk = new ArrayList<TextPosition>();
            for (var position : positions) {
                if (!chunk.isEmpty() && position.getXDirAdj() - right(chunk.getLast()) > position.getWidthOfSpace() / 2) {
                    flush(chunk);
                }
                if (position.getUnicode().codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
                    flush(chunk);
                } else {
                    chunk.add(position);
                }
            }
            flush(chunk);
        }

        private void flush(List<TextPosition> chunk) {
            if (chunk.isEmpty()) {
                return;
            }
            var bottom = (float) chunk.stream().mapToDouble(TextPosition::getYDirAdj).max().orElseThrow();
            var height = (float) chunk.stream().mapToDouble(TextPosition::getHeightDir).max().orElseThrow();
            words.add(new Word(
                    chunk.stream().map(TextPosition::getUnicode).collect(Collectors.joining()),
                    (float) chunk.stream().mapToDouble(TextPosition::getXDirAdj).min().orElseThrow(),
                    (float) chunk.stream().mapToDouble(WordCollector::right).max().orElseThrow(),
                    bottom - height,
                    bottom));
            chunk.clear();
        }

        private static float right(TextPosition position) {
            return position.getXDirAdj() + position.getWidthDirAdj();
        }
    }

    private static class BoxCollector extends PDFGraphicsStreamEngine {

        private final List<Box> boxes = new ArrayList<>();
        private final float pageHeight;
        private final List<Subpath> subpaths = new ArrayList<>();

        BoxCollector(PDPage page) {
            super(page);
            pageHeight = page.getMediaBox().getHeight();
        }

        List<Box> collect() throws IOException {
            processPage(getPage());
            return boxes;
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            subpaths.add(new Subpath(new ArrayList<>(List.of(p0, p1, p2, p3))));
        }

        @Override
        public void moveTo(float x, float y) {
            subpaths.add(new Subpath(new ArrayList<>(List.of(new Point2D.Float(x, y)))));
        }

        @Override
        public void lineTo(float x, float y) {
            var last = getCurrentPoint();
            var subpath = subpaths.getLast();
            subpath.setDiagonal(subpath.isDiagonal() || Math.abs(last.getX() - x) > 1 && Math.abs(last.getY() - y) > 1);
            subpath.getPoints().add(new Point2D.Float(x, y));
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            subpaths.getLast().setDiagonal(true);
            subpaths.getLast().getPoints().add(new Point2D.Float(x3, y3));
        }

        @Override
        public Point2D getCurrentPoint() {
            return subpaths.isEmpty() ? new Point2D.Float() : subpaths.getLast().getPoints().getLast();
        }

        @Override
        public void strokePath() throws IOException {
            paint(getGraphicsState().getStrokingColor());
        }

        @Override
        public void fillPath(int windingRule) throws IOException {
            paint(getGraphicsState().getNonStrokingColor());
        }

        @Override
        public void fillAndStrokePath(int windingRule) throws IOException {
            paint(getGraphicsState().getStrokingColor());
        }

        @Override
        public void endPath() {
            subpaths.clear();
        }

        @Override
        public void closePath() {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void drawImage(PDImage pdImage) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }

        private void paint(PDColor color) throws IOException {
            var rgb = color.toRGB();
            var dark = Math.max(rgb >> 16 & 0xff, Math.max(rgb >> 8 & 0xff, rgb & 0xff)) < 77;
            for (var subpath : subpaths) {
                var xs = subpath.getPoints().stream().mapToDouble(Point2D::getX).summaryStatistics();
                var ys = subpath.getPoints().stream().mapToDouble(Point2D::getY).summaryStatistics();
                boxes.add(new Box((float) xs.getMin(), (float) xs.getMax(),
                        pageHeight - (float) ys.getMax(), pageHeight - (float) ys.getMin(), dark, subpath.isDiagonal()));
            }
            endPath();
        }
    }
}
