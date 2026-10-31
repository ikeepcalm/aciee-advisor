package ro.aciee.advisor.domain.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.aciee.advisor.domain.audit.model.AuditEntry;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<AuditEntry> history(String tableName, Integer rowId, Long before) {
        return jdbc.sql("""
                        SELECT l.id, l.created_at, a.email AS actor, l.table_name, l.row_id, l.op,
                               l.old::text AS old, l.new::text AS current
                        FROM core.audit_log l
                        LEFT JOIN core.user_account a ON a.id = l.actor_user_id
                        WHERE (CAST(:table AS text) IS NULL OR l.table_name = :table)
                          AND (CAST(:row AS integer) IS NULL OR l.row_id = :row)
                          AND (CAST(:before AS bigint) IS NULL OR l.id < :before)
                        ORDER BY l.id DESC
                        LIMIT 50""")
                .param("table", tableName)
                .param("row", rowId)
                .param("before", before)
                .query(AuditEntry.class)
                .list();
    }
}
