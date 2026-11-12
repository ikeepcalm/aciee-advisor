package ro.aciee.advisor.domain.timetable.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.timetable.model.Timetable;
import ro.aciee.advisor.domain.timetable.model.TimetableChange;
import ro.aciee.advisor.domain.timetable.service.CalendarService;
import ro.aciee.advisor.domain.timetable.service.TimetableChangeService;
import ro.aciee.advisor.domain.timetable.service.TimetableService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;
    private final CalendarService calendarService;
    private final TimetableChangeService timetableChangeService;

    @GetMapping("/api/timetable")
    public Timetable timetable() {
        return timetableService.current();
    }

    @GetMapping("/api/timetable/changes")
    public List<TimetableChange> changes(@RequestParam String program, @RequestParam String group) {
        return timetableChangeService.recent(program, group);
    }

    @GetMapping("/api/timetable/calendar.ics")
    public ResponseEntity<String> calendar(@RequestParam String program, @RequestParam String group,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate odd) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName(group) + "\"")
                .body(calendarService.calendar(program, group, odd));
    }

    private static String fileName(String group) {
        String safe = group.replaceAll("[^A-Za-z0-9]", "");
        return safe.isEmpty() ? "orar.ics" : "orar-" + safe + ".ics";
    }
}
