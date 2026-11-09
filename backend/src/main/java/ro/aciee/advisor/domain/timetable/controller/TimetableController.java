package ro.aciee.advisor.domain.timetable.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.timetable.model.Timetable;
import ro.aciee.advisor.domain.timetable.service.TimetableService;

@RestController
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    @GetMapping("/api/timetable")
    public Timetable timetable() {
        return timetableService.current();
    }
}
