package ro.aciee.advisor.domain.submission.task;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AggregateRefreshTask {

    private final JdbcClient jdbc;

    @Scheduled(cron = "0 0 * * * *")
    public void refresh() {
        jdbc.sql("SELECT anon.refresh_aggregates()").query().listOfRows();
    }
}
