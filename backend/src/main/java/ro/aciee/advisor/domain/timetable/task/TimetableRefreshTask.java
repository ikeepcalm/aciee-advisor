package ro.aciee.advisor.domain.timetable.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.aciee.advisor.domain.timetable.service.CalendarService;
import ro.aciee.advisor.domain.timetable.service.CourseLinkService;
import ro.aciee.advisor.domain.timetable.service.TeacherImportService;
import ro.aciee.advisor.domain.timetable.service.TimetableChangeService;
import ro.aciee.advisor.domain.timetable.service.TimetableService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimetableRefreshTask {

    private final TimetableService timetableService;
    private final TimetableChangeService timetableChangeService;
    private final CourseLinkService courseLinkService;
    private final CalendarService calendarService;
    private final TeacherImportService teacherImportService;

    @Scheduled(initialDelay = 0, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void refresh() throws InterruptedException {
        try {
            timetableService.refresh();
        } catch (IOException e) {
            log.warn("Timetable refresh failed, keeping the previous copy: {}", e.toString());
            return;
        }
        for (var program : timetableService.current().getPrograms()) {
            try {
                courseLinkService.attach(program);
                program.setWeeks(calendarService.weeks(program));
                int changes = timetableChangeService.record(program);
                if (changes > 0) {
                    log.info("Timetable {} changed: {} slot changes", program.getCode(), changes);
                }
            } catch (RuntimeException e) {
                log.warn("Timetable {} links or changes not recorded: {}", program.getCode(), e.toString());
            }
        }
        try {
            int imported = teacherImportService.importTeachers(timetableService.current().getPrograms());
            if (imported > 0) {
                log.info("Imported {} teaching assignments from the timetable", imported);
            }
        } catch (RuntimeException e) {
            log.warn("Teachers not imported from the timetable: {}", e.toString());
        }
    }
}
