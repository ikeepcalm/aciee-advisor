package ro.aciee.advisor.domain.timetable.service;

import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;
import ro.aciee.advisor.domain.timetable.model.source.WeekParity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HexFormat;
import java.util.Map;

public final class CalendarWriter {

    public static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Map<String, String> KINDS = Map.of("C", "Curs", "S", "Seminar", "L", "Laborator", "P", "Proiect");
    private static final String TIMEZONE = String.join("\r\n",
            "BEGIN:VTIMEZONE",
            "TZID:Europe/Bucharest",
            "BEGIN:DAYLIGHT",
            "TZOFFSETFROM:+0200",
            "TZOFFSETTO:+0300",
            "TZNAME:EEST",
            "DTSTART:19700329T030000",
            "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
            "END:DAYLIGHT",
            "BEGIN:STANDARD",
            "TZOFFSETFROM:+0300",
            "TZOFFSETTO:+0200",
            "TZNAME:EET",
            "DTSTART:19701025T040000",
            "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU",
            "END:STANDARD",
            "END:VTIMEZONE");

    private CalendarWriter() {
    }

    public static String write(TimetableProgram program, String group, LocalDate oddMonday, LocalDate firstDay,
                               LocalDate lastDay, LocalDate ratingOpens, String publicUrl) {
        var lines = new StringBuilder();
        Instant stamp = program.getUpdatedAt() == null ? Instant.EPOCH : program.getUpdatedAt();
        line(lines, "BEGIN:VCALENDAR");
        line(lines, "VERSION:2.0");
        line(lines, "PRODID:-//ACIEE Advisor//Orar//RO");
        line(lines, "CALSCALE:GREGORIAN");
        line(lines, "METHOD:PUBLISH");
        line(lines, "X-WR-CALNAME:" + text("Orar " + group));
        line(lines, "X-WR-TIMEZONE:Europe/Bucharest");
        line(lines, "REFRESH-INTERVAL;VALUE=DURATION:PT6H");
        line(lines, "X-PUBLISHED-TTL:PT6H");
        lines.append(TIMEZONE).append("\r\n");
        for (Lesson lesson : program.getLessons()) {
            if (!lesson.getGroups().contains(group)) {
                continue;
            }
            LocalDate first = firstOccurrence(lesson, oddMonday, firstDay);
            if (first.isAfter(lastDay)) {
                continue;
            }
            String kind = lesson.getKind() == null ? null : KINDS.get(lesson.getKind().trim());
            String name = lesson.getCourse();
            String link = lesson.getCourseId() == null ? null : publicUrl + "/courses/" + lesson.getCourseId();
            var description = new StringBuilder();
            if (lesson.getTeacher() != null) {
                description.append(lesson.getTeacher()).append('\n');
            }
            if (lesson.getWeeks() != WeekParity.ALL) {
                description.append(lesson.getWeeks() == WeekParity.ODD ? "Săptămâna impară" : "Săptămâna pară").append('\n');
            }
            description.append(link != null ? "Ce spun colegii despre curs: " + link : "Orarul complet: " + publicUrl + "/timetable");
            line(lines, "BEGIN:VEVENT");
            line(lines, "UID:" + uid(program.getCode(), group, lesson) + "@acieeadvisor");
            line(lines, "DTSTAMP:" + UTC.format(stamp));
            line(lines, "DTSTART;TZID=Europe/Bucharest:" + LOCAL.format(first.atTime(LocalTime.parse(lesson.getStart()))));
            line(lines, "DTEND;TZID=Europe/Bucharest:" + LOCAL.format(first.atTime(LocalTime.parse(lesson.getEnd()))));
            line(lines, "RRULE:FREQ=WEEKLY;INTERVAL=" + (lesson.getWeeks() == WeekParity.ALL ? 1 : 2)
                    + ";UNTIL=" + UTC.format(lastDay.atTime(LocalTime.MAX).atZone(BUCHAREST)));
            line(lines, "SUMMARY:" + text(kind == null ? name : name + " (" + kind + ")"));
            if (lesson.getRoom() != null) {
                line(lines, "LOCATION:" + text(lesson.getRoom()));
            }
            line(lines, "DESCRIPTION:" + text(description.toString()));
            if (link != null) {
                line(lines, "URL:" + link);
            }
            line(lines, "END:VEVENT");
        }
        if (ratingOpens != null) {
            line(lines, "BEGIN:VEVENT");
            line(lines, "UID:rate-" + program.getCode() + "-" + DATE.format(ratingOpens) + "@acieeadvisor");
            line(lines, "DTSTAMP:" + UTC.format(stamp));
            line(lines, "DTSTART;VALUE=DATE:" + DATE.format(ratingOpens));
            line(lines, "DTEND;VALUE=DATE:" + DATE.format(ratingOpens.plusDays(1)));
            line(lines, "SUMMARY:" + text("Evaluează cursurile semestrului (~2 min)"));
            line(lines, "DESCRIPTION:" + text("Anonim. Spune-le colegilor ce îi așteaptă: " + publicUrl + "/rate"));
            line(lines, "URL:" + publicUrl + "/rate");
            line(lines, "END:VEVENT");
        }
        line(lines, "END:VCALENDAR");
        return lines.toString();
    }

    public static LocalDate firstOccurrence(Lesson lesson, LocalDate oddMonday, LocalDate firstDay) {
        LocalDate date = firstDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(lesson.getDay())));
        if (lesson.getWeeks() == WeekParity.ALL) {
            return date;
        }
        long weeks = ChronoUnit.WEEKS.between(oddMonday, date.with(DayOfWeek.MONDAY));
        boolean odd = Math.floorMod(weeks, 2) == 0;
        return odd == (lesson.getWeeks() == WeekParity.ODD) ? date : date.plusWeeks(1);
    }

    private static String uid(String program, String group, Lesson lesson) {
        String key = String.join("|", program, group, String.valueOf(lesson.getDay()), lesson.getStart(), lesson.getEnd(),
                String.valueOf(lesson.getKind()), lesson.getSubject(), lesson.getWeeks().name(),
                String.valueOf(lesson.getRoom()), String.valueOf(lesson.getTeacher()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    private static void line(StringBuilder out, String content) {
        int limit = 75;
        var current = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (used + size > limit) {
                out.append(current).append("\r\n ");
                current.setLength(0);
                used = 0;
                limit = 74;
            }
            current.appendCodePoint(codePoint);
            used += size;
            offset += Character.charCount(codePoint);
        }
        out.append(current).append("\r\n");
    }
}
