package ro.aciee.advisor.domain.audit.task;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditPartitionTask {

    private final JdbcClient jdbc;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 3 1 * *", zone = "UTC")
    public void ensurePartitions() {
        jdbc.sql("SELECT core.ensure_audit_partitions(3)").query().listOfRows();
    }
}
