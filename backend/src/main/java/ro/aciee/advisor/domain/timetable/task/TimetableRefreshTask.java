package ro.aciee.advisor.domain.timetable.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.aciee.advisor.domain.timetable.service.TimetableService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimetableRefreshTask {

    private final TimetableService timetableService;

    @Scheduled(initialDelay = 0, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void refresh() throws InterruptedException {
        try {
            timetableService.refresh();
        } catch (IOException e) {
            log.warn("Timetable refresh failed, keeping the previous copy: {}", e.toString());
        }
    }
}
