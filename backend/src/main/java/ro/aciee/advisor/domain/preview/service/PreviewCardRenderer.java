package ro.aciee.advisor.domain.preview.service;

import org.springframework.stereotype.Component;
import ro.aciee.advisor.domain.preview.model.PreviewCard;
import ro.aciee.advisor.domain.preview.model.PreviewMetric;
import ro.aciee.advisor.domain.preview.type.PreviewPage;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PreviewCardRenderer {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 630;
    private static final int LEFT = 78;
    private static final int CONTENT_WIDTH = WIDTH - 2 * LEFT;
    private static final int CONTENT_TOP = 60;
    private static final int CONTENT_BOTTOM = 512;
    private static final int FOOTER_BASELINE = 568;
    private static final Color BOARD = new Color(0xf7f3e6);
    private static final Color PANEL = new Color(0xfffdf5);
    private static final Color INK = new Color(0x20263d);
    private static final Color MUTED = new Color(0x5e6272);
    private static final Color LINE = new Color(0x9d9785);
    private static final Color MASK = new Color(0x2946b3);
    private static final Color COPPER = new Color(0xc13a2c);
    private static final Color MARK = new Color(0xffe270);
    private static final float[] WOBBLE = {90, 7, 7, 70, 80, 8, 8, 60};
    private static final float[] ROUND = {6, 6, 6, 6, 6, 6, 6, 6};
    private static final int[][] BLOCKS = {{0, 42, 14}, {0, 60, 10}, {1, 42, 10}, {1, 56, 22}, {2, 48, 14}, {3, 42, 22}, {3, 68, 14}, {4, 42, 10}};
    private static final float[] WOBBLE_ALT = {8, 60, 80, 8, 7, 70, 90, 7};
    private static final int[][] TITLE_ABOVE_METRICS = {{1, 112, 84}, {2, 80, 60}};
    private static final int[][] TITLE_ALONE = {{1, 128, 116}, {2, 124, 80}, {3, 76, 60}};
    private static final int[][] PUNCHLINE = {{1, 76, 52}, {2, 56, 44}};
    private static final Pattern SCORE = Pattern.compile("^(\\d+(?:[.,]\\d+)?)(/\\d+)?$");

    private final Font display = load("fonts/Caveat-Bold.ttf");
    private final Font body = load("fonts/IBMPlexSans-Regular.ttf");
    private final Font bodyStrong = load("fonts/IBMPlexSans-SemiBold.ttf");

    private record TextBlock(Font font, List<String> lines) {
        int size() {
            return font.getSize();
        }
    }

    public byte[] render(PreviewCard card) {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            var random = new Random((card.getEyebrow() + card.getTitle()).hashCode());
            paintPaper(g);
            paintSheet(g, random);
            List<PreviewMetric> metrics = card.getMetrics().subList(0, Math.min(card.getMetrics().size(), 4));
            PreviewPage page = card.getPage();
            if (metrics.isEmpty()) {
                paintStatement(g, card, page == null ? CONTENT_WIDTH : CONTENT_WIDTH - 330, random);
            } else {
                paintScores(g, card, metrics, page == null ? CONTENT_WIDTH : CONTENT_WIDTH - 190, random);
            }
            if (page != null) {
                int size = metrics.isEmpty() ? 290 : 150;
                paintMotif(g, page, WIDTH - LEFT - size, metrics.isEmpty() ? 148 : 76, size, random);
            }
            paintFooter(g, metrics.isEmpty() ? null : card.getNote());
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private void paintPaper(Graphics2D g) {
        g.setColor(BOARD);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(alpha(INK, 22));
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x < WIDTH; x += 24) {
            g.drawLine(x, 0, x, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += 24) {
            g.drawLine(0, y, WIDTH, y);
        }
    }

    private void paintSheet(Graphics2D g, Random random) {
        Shape sheet = wobbly(30, 26, WIDTH - 60, HEIGHT - 52, WOBBLE, 3, random);
        Shape under = AffineTransform.getRotateInstance(Math.toRadians(1.1), WIDTH / 2.0, HEIGHT / 2.0)
                .createTransformedShape(sheet);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Shape paper : List.of(under, sheet)) {
            g.setColor(PANEL);
            g.fill(paper);
            g.setColor(LINE);
            g.draw(paper);
        }
        AffineTransform upright = g.getTransform();
        g.rotate(Math.toRadians(-3), 980, 28);
        g.setColor(alpha(MARK, 215));
        g.fill(wobbly(900, 10, 170, 36, new float[]{4, 4, 4, 4, 4, 4, 4, 4}, 2, random));
        g.setTransform(upright);
    }

    private void paintScores(Graphics2D g, PreviewCard card, List<PreviewMetric> metrics, int titleWidth, Random random) {
        TextBlock title = fit(g, display, card.getTitle(), titleWidth, TITLE_ABOVE_METRICS);
        int titleHeight = titleHeight(title);
        int room = CONTENT_BOTTOM - CONTENT_TOP - 46 - titleHeight - 22;
        int tileHeight = Math.clamp(room, 150, 236);
        int top = CONTENT_TOP + Math.max(0, (room - tileHeight) / 2);
        paintEyebrow(g, card.getEyebrow(), top + 36);
        int tilesTop = paintTitle(g, title, top + 46) + 22;
        int gap = 18;
        double weights = metrics.stream().mapToDouble(this::weight).sum();
        double unit = (CONTENT_WIDTH - gap * (metrics.size() - 1)) / weights;
        double x = LEFT;
        for (int i = 0; i < metrics.size(); i++) {
            double width = unit * weight(metrics.get(i));
            paintTile(g, metrics.get(i), (int) x, tilesTop, (int) width, tileHeight, i % 2 == 0 ? WOBBLE : WOBBLE_ALT, random);
            x += width + gap;
        }
    }

    private double weight(PreviewMetric metric) {
        return metric.isHeadline() ? 1.3 : 1;
    }

    private void paintTile(Graphics2D g, PreviewMetric metric, int x, int y, int width, int height, float[] radii, Random random) {
        Shape tile = wobbly(x, y, width, height, radii, 2.5f, random);
        g.setColor(alpha(INK, 72));
        g.fill(AffineTransform.getTranslateInstance(4, 6).createTransformedShape(tile));
        g.setColor(PANEL);
        g.fill(tile);
        g.setColor(alpha(INK, 200));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(tile);

        int inner = width - 44;
        Font labelFont = body.deriveFont(27f);
        g.setFont(labelFont);
        g.setColor(MUTED);
        int baseline = y + 46;
        for (String line : clamp(g.getFontMetrics(), metric.getLabel(), 2, inner)) {
            g.drawString(line, x + 22, baseline);
            baseline += 32;
        }

        Matcher score = SCORE.matcher(metric.getValue());
        String main = score.matches() ? score.group(1) : metric.getValue();
        String suffix = score.matches() && score.group(2) != null ? score.group(2) : "";
        Font valueFamily = score.matches() ? bodyStrong : display;
        int size = (metric.isHeadline() ? 112 : 84) + (score.matches() ? 0 : 16);
        while (size > 36 && valueWidth(g, valueFamily, main, suffix, size) > inner) {
            size -= 4;
        }
        Font mainFont = valueFamily.deriveFont((float) size);
        int mainWidth = g.getFontMetrics(mainFont).stringWidth(main);
        int valueBaseline = y + height - 34;
        if (metric.isHeadline()) {
            paintMarker(g, x + 22, valueBaseline, mainWidth, size, random);
        }
        g.setFont(mainFont);
        g.setColor(INK);
        g.drawString(main, x + 22, valueBaseline);
        g.setFont(suffixFont(size));
        g.setColor(MUTED);
        g.drawString(suffix, x + 22 + mainWidth + size * 0.08f, valueBaseline);
    }

    private int valueWidth(Graphics2D g, Font family, String main, String suffix, int size) {
        return g.getFontMetrics(family.deriveFont((float) size)).stringWidth(main)
                + (suffix.isEmpty() ? 0 : (int) (size * 0.08f) + g.getFontMetrics(suffixFont(size)).stringWidth(suffix));
    }

    private Font suffixFont(int size) {
        return body.deriveFont(size * 0.4f);
    }

    private void paintStatement(Graphics2D g, PreviewCard card, int width, Random random) {
        TextBlock title = fit(g, display, card.getTitle(), width, TITLE_ALONE);
        String note = card.getNote().strip();
        int split = Math.max(note.lastIndexOf(". "), Math.max(note.lastIndexOf("! "), note.lastIndexOf("? ")));
        String lead = split < 0 ? "" : note.substring(0, split + 1);
        Font leadFont = body.deriveFont(36f);
        List<String> leadLines = lead.isEmpty() ? List.of()
                : clamp(g.getFontMetrics(leadFont), lead, 2, width);
        TextBlock punchline = fit(g, display, note.substring(split + 1).strip(), width - 24, PUNCHLINE);
        int punchlineStep = (int) (punchline.size() * 1.12f);
        int noteHeight = note.isEmpty() ? 0 : 20 + leadLines.size() * 46 + punchline.lines().size() * punchlineStep + 8;
        int top = CONTENT_TOP + Math.max(0, (CONTENT_BOTTOM - CONTENT_TOP - 46 - titleHeight(title) - noteHeight) / 2);
        paintEyebrow(g, card.getEyebrow(), top + 36);
        int baseline = paintTitle(g, title, top + 46) + 20;
        if (note.isEmpty()) {
            return;
        }
        g.setFont(leadFont);
        g.setColor(MUTED);
        for (String line : leadLines) {
            baseline += 46;
            g.drawString(line, LEFT, baseline - 10);
        }
        for (String line : punchline.lines()) {
            baseline += punchlineStep;
            int lineBaseline = baseline - (int) (punchline.size() * 0.3f);
            paintMarker(g, LEFT + 10, lineBaseline, g.getFontMetrics(punchline.font()).stringWidth(line), punchline.size(), random);
            g.setFont(punchline.font());
            g.setColor(INK);
            g.drawString(line, LEFT + 10, lineBaseline);
        }
    }

    private void paintEyebrow(Graphics2D g, String eyebrow, int baseline) {
        g.setColor(COPPER);
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(scribble(LEFT, baseline - 18, 46, 12));
        g.setFont(display.deriveFont(40f));
        FontMetrics metrics = g.getFontMetrics();
        int width = CONTENT_WIDTH - 60 - 150;
        g.drawString(clamp(metrics, eyebrow, 1, width).getFirst(), LEFT + 60, baseline);
    }

    private int titleHeight(TextBlock title) {
        return (int) (title.size() * (0.8f + 0.92f * (title.lines().size() - 1) + 0.26f));
    }

    private int paintTitle(Graphics2D g, TextBlock title, int top) {
        g.setFont(title.font());
        g.setColor(INK);
        float baseline = top + title.size() * 0.8f;
        for (String line : title.lines()) {
            g.drawString(line, LEFT, baseline);
            baseline += title.size() * 0.92f;
        }
        return top + titleHeight(title);
    }

    private void paintMarker(Graphics2D g, float x, float baseline, float width, int size, Random random) {
        float broad = size * 0.42f;
        float tight = size * 0.14f;
        g.setColor(MARK);
        g.fill(wobbly(x - size * 0.12f, baseline - size * 0.56f, width + size * 0.24f, size * 0.76f,
                new float[]{broad, broad, tight, tight, broad, broad, tight, tight}, size * 0.03f, random));
    }

    private void paintMotif(Graphics2D g, PreviewPage page, double x, double y, double size, Random random) {
        AffineTransform upright = g.getTransform();
        g.translate(x, y);
        g.scale(size / 100, size / 100);
        g.rotate(Math.toRadians(-4), 50, 50);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (page) {
            case HOME -> paintLogo(g, 2, 4, 96);
            case COURSES -> {
                paintBook(g, 12, 70, 80, MASK, random);
                paintBook(g, 4, 46, 78, MARK, random);
                g.rotate(Math.toRadians(-7), 50, 34);
                paintBook(g, 16, 22, 72, COPPER, random);
            }
            case PROFESSORS -> {
                var shoulders = new Path2D.Float();
                shoulders.moveTo(6, 94);
                shoulders.curveTo(6, 64, 24, 58, 38, 58);
                shoulders.curveTo(52, 58, 70, 64, 70, 94);
                shoulders.closePath();
                ink(g, shoulders, MASK);
                ink(g, new Ellipse2D.Float(20, 16, 36, 36), PANEL);
                ink(g, star(76, 30, 21), MARK);
            }
            case TIMETABLE -> {
                Shape sheet = wobbly(8, 16, 84, 78, ROUND, 1, random);
                g.setColor(PANEL);
                g.fill(sheet);
                g.setClip(sheet);
                g.setColor(COPPER);
                g.fillRect(0, 0, 100, 34);
                g.setClip(null);
                g.setColor(INK);
                g.draw(sheet);
                g.draw(new Line2D.Float(8, 34, 92, 34));
                g.draw(new Line2D.Float(30, 9, 30, 23));
                g.draw(new Line2D.Float(70, 9, 70, 23));
                g.setStroke(new BasicStroke(1.6f));
                for (int[] block : BLOCKS) {
                    ink(g, wobbly(14.5f + block[0] * 14.6f, block[1], 12, block[2], new float[]{2, 2, 2, 2, 2, 2, 2, 2}, 0, random),
                            block[1] == 56 || block[1] == 48 ? MASK : MARK);
                }
            }
            case GUIDE -> {
                ink(g, new Ellipse2D.Float(43, 1, 14, 14), PANEL);
                ink(g, new Ellipse2D.Float(6, 10, 86, 86), MARK);
                ink(g, new Ellipse2D.Float(17, 21, 64, 64), PANEL);
                g.rotate(Math.toRadians(38), 49, 53);
                ink(g, triangle(49, 27, 49, 53), COPPER);
                ink(g, triangle(49, 79, 49, 53), MASK);
            }
            case RATE -> {
                g.rotate(Math.toRadians(9), 50, 30);
                ink(g, wobbly(31, 4, 38, 50, ROUND, 1, random), PANEL);
                g.setColor(COPPER);
                g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                var tick = new Path2D.Float();
                tick.moveTo(40, 24);
                tick.lineTo(47, 32);
                tick.lineTo(60, 14);
                g.draw(tick);
                g.rotate(Math.toRadians(-9), 50, 30);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                ink(g, wobbly(10, 46, 80, 48, ROUND, 1, random), MASK);
                ink(g, star(50, 72, 15), MARK);
                g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Float(26, 46, 74, 46));
            }
        }
        g.setTransform(upright);
    }

    private void paintBook(Graphics2D g, float x, float y, float width, Color cover, Random random) {
        ink(g, wobbly(x, y, width, 24, ROUND, 1, random), cover);
        ink(g, wobbly(x + 15, y + 6, width - 15, 12, new float[8], 0, random), PANEL);
    }

    private void ink(Graphics2D g, Shape shape, Color fill) {
        g.setColor(fill);
        g.fill(shape);
        g.setColor(INK);
        g.draw(shape);
    }

    private Shape star(double x, double y, double radius) {
        var path = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * i / 5 - Math.PI / 2;
            double r = i % 2 == 0 ? radius : radius * 0.46;
            if (i == 0) {
                path.moveTo(x + r * Math.cos(angle), y + r * Math.sin(angle));
            } else {
                path.lineTo(x + r * Math.cos(angle), y + r * Math.sin(angle));
            }
        }
        path.closePath();
        return path;
    }

    private Shape triangle(float tipX, float tipY, float baseX, float baseY) {
        var path = new Path2D.Float();
        path.moveTo(tipX, tipY);
        path.lineTo(baseX - 8, baseY);
        path.lineTo(baseX + 8, baseY);
        path.closePath();
        return path;
    }

    private void paintFooter(Graphics2D g, String note) {
        paintLogo(g, LEFT - 5, FOOTER_BASELINE - 39, 52);
        g.setFont(display.deriveFont(48f));
        g.setColor(INK);
        int x = LEFT + 54;
        g.drawString("ACIEE", x, FOOTER_BASELINE);
        x += g.getFontMetrics().stringWidth("ACIEE ");
        g.setColor(MASK);
        g.drawString("Advisor", x, FOOTER_BASELINE);
        if (note != null) {
            g.setFont(bodyStrong.deriveFont(30f));
            g.setColor(MUTED);
            FontMetrics metrics = g.getFontMetrics();
            String text = clamp(metrics, note, 1, WIDTH / 2).getFirst();
            int noteX = WIDTH - LEFT - metrics.stringWidth(text);
            g.drawString(text, noteX, FOOTER_BASELINE - 4);
            g.setColor(COPPER);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(scribble(noteX, FOOTER_BASELINE + 8, metrics.stringWidth(text), 10));
        }
    }

    private void paintLogo(Graphics2D g, double x, double y, double size) {
        var bubble = new Path2D.Double();
        bubble.moveTo(3.6, 5.2);
        bubble.curveTo(11, 3.6, 22, 3.8, 28.6, 5.1);
        bubble.curveTo(29.6, 9.7, 29.5, 15.9, 28.7, 21.3);
        bubble.curveTo(23.2, 22, 17.7, 21.9, 13.4, 21.8);
        bubble.lineTo(6.6, 28.3);
        bubble.lineTo(7.5, 21.8);
        bubble.curveTo(6.1, 21.8, 4.9, 21.6, 3.8, 21.3);
        bubble.curveTo(3, 16, 3, 10, 3.6, 5.2);
        bubble.closePath();
        var wave = new Path2D.Double();
        wave.moveTo(7.8, 17);
        wave.lineTo(12, 17);
        wave.lineTo(12, 9.5);
        wave.lineTo(18, 9.5);
        wave.lineTo(18, 17);
        wave.lineTo(24, 17);
        wave.lineTo(24, 9.5);
        wave.lineTo(25.6, 9.5);
        AffineTransform upright = g.getTransform();
        g.translate(x, y);
        g.scale(size / 32, size / 32);
        g.setColor(MARK);
        g.fill(bubble);
        g.setColor(INK);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(bubble);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(wave);
        g.setTransform(upright);
    }

    private Shape scribble(float x, float y, float width, float height) {
        var path = new Path2D.Float();
        path.moveTo(2, 6.5);
        path.curveTo(14, 3, 24, 8, 38, 5.2);
        path.curveTo(52, 2.4, 62, 3, 74, 5.6);
        path.curveTo(86, 8.2, 90, 7, 98, 4);
        var placement = AffineTransform.getTranslateInstance(x, y);
        placement.scale(width / 100, height / 10);
        return placement.createTransformedShape(path);
    }

    private Shape wobbly(float x, float y, float width, float height, float[] radii, float jitter, Random random) {
        float[] corners = {x, y, x + width, y, x + width, y + height, x, y + height};
        for (int i = 0; i < corners.length; i++) {
            corners[i] += (random.nextFloat() * 2 - 1) * jitter;
        }
        float[] r = new float[8];
        for (int i = 0; i < 8; i++) {
            r[i] = Math.min(radii[i], (i % 2 == 0 ? width : height) * 0.48f);
        }
        float k = 0.4477f;
        var path = new Path2D.Float();
        path.moveTo(corners[0] + r[0], corners[1]);
        path.lineTo(corners[2] - r[2], corners[3]);
        path.curveTo(corners[2] - r[2] * k, corners[3], corners[2], corners[3] + r[3] * k, corners[2], corners[3] + r[3]);
        path.lineTo(corners[4], corners[5] - r[5]);
        path.curveTo(corners[4], corners[5] - r[5] * k, corners[4] - r[4] * k, corners[5], corners[4] - r[4], corners[5]);
        path.lineTo(corners[6] + r[6], corners[7]);
        path.curveTo(corners[6] + r[6] * k, corners[7], corners[6], corners[7] - r[7] * k, corners[6], corners[7] - r[7]);
        path.lineTo(corners[0], corners[1] + r[1]);
        path.curveTo(corners[0], corners[1] + r[1] * k, corners[0] + r[0] * k, corners[1], corners[0] + r[0], corners[1]);
        path.closePath();
        return path;
    }

    private TextBlock fit(Graphics2D g, Font family, String text, int maxWidth, int[][] tiers) {
        for (int[] tier : tiers) {
            for (int size = tier[1]; size >= tier[2]; size -= 4) {
                Font font = family.deriveFont((float) size);
                FontMetrics metrics = g.getFontMetrics(font);
                List<String> lines = wrap(metrics, text, maxWidth);
                if (lines.size() <= tier[0] && lines.stream().allMatch(line -> metrics.stringWidth(line) <= maxWidth)) {
                    return new TextBlock(font, balance(metrics, text, lines, maxWidth));
                }
            }
        }
        int[] smallest = tiers[tiers.length - 1];
        Font font = family.deriveFont((float) smallest[2]);
        FontMetrics metrics = g.getFontMetrics(font);
        return new TextBlock(font, clamp(metrics, text, smallest[0], maxWidth));
    }

    private List<String> balance(FontMetrics metrics, String text, List<String> lines, int maxWidth) {
        List<String> balanced = lines;
        for (int width = maxWidth - 24; width > maxWidth / 2; width -= 24) {
            List<String> narrower = wrap(metrics, text, width);
            if (narrower.size() != lines.size() || narrower.stream().anyMatch(line -> metrics.stringWidth(line) > maxWidth)) {
                break;
            }
            balanced = narrower;
        }
        return balanced;
    }

    private List<String> wrap(FontMetrics metrics, String text, int maxWidth) {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                candidate = word;
            }
            line.setLength(0);
            line.append(candidate);
        }
        lines.add(line.toString());
        return lines;
    }

    private List<String> clamp(FontMetrics metrics, String text, int maxLines, int maxWidth) {
        List<String> lines = wrap(metrics, text, maxWidth);
        var kept = new ArrayList<>(lines.subList(0, Math.min(lines.size(), maxLines)));
        for (int i = 0; i < kept.size(); i++) {
            String line = kept.get(i);
            boolean cut = i == maxLines - 1 && lines.size() > maxLines;
            while (!line.isEmpty() && metrics.stringWidth(cut ? line + "…" : line) > maxWidth) {
                line = line.substring(0, line.length() - 1).stripTrailing();
                cut = true;
            }
            kept.set(i, cut ? line + "…" : line);
        }
        return kept;
    }

    private static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Font load(String resource) {
        try (InputStream in = PreviewCardRenderer.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing font " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("cannot load font " + resource, e);
        }
    }
}
