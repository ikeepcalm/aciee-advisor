package ro.aciee.advisor.domain.moderation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.moderation.model.Queue;
import ro.aciee.advisor.domain.moderation.model.QueueItem;
import ro.aciee.advisor.domain.moderation.model.ReportedPost;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public Queue queue() {
        var queue = new Queue();
        queue.setTotal(jdbc.sql("SELECT count(*) FROM anon.moderation_queue").query(Long.class).single());
        queue.setItems(jdbc.sql("""
                        SELECT q.post_id, q.version_no, q.kind, q.body, q.submitted_week AS week, o.title_snapshot AS course,
                               (SELECT v.body FROM anon.post_version v
                                WHERE v.post_id = q.post_id AND v.status = 'approved'
                                ORDER BY v.version_no DESC LIMIT 1) AS previous_body
                        FROM anon.moderation_queue q
                        JOIN core.course_offering o ON o.id = q.offering_id
                        ORDER BY q.submitted_week, q.post_id, q.version_no
                        LIMIT 20""")
                .query(QueueItem.class)
                .list());
        return queue;
    }

    @Transactional
    public void decide(UUID postId, int versionNo, String reasonCode) {
        int updated = jdbc.sql("""
                        UPDATE anon.post_version
                        SET status = CASE WHEN CAST(:reason AS text) IS NULL THEN 'approved' ELSE 'rejected' END,
                            reason_code = :reason, moderated_week = date_trunc('week', current_date)::date
                        WHERE post_id = :post AND version_no = :version AND status = 'pending'""")
                .param("reason", reasonCode, Types.VARCHAR)
                .param("post", postId)
                .param("version", versionNo)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_moderated");
        }
    }

    @Transactional(readOnly = true)
    public List<ReportedPost> reports() {
        return jdbc.sql("""
                        SELECT r.post_id, p.kind, o.title_snapshot AS course,
                               coalesce(pp.body, (SELECT v.body FROM anon.post_version v WHERE v.post_id = p.id
                                                  ORDER BY v.version_no DESC LIMIT 1)) AS body,
                               string_agg(DISTINCT rr.label_ro, ', ') AS reasons, count(*) AS count, max(r.reported_week) AS week
                        FROM anon.post_report r
                        JOIN anon.post p ON p.id = r.post_id
                        JOIN core.course_offering o ON o.id = p.offering_id
                        JOIN ref.report_reason rr ON rr.code = r.reason_code
                        LEFT JOIN anon.published_post pp ON pp.id = p.id
                        WHERE r.status = 'open'
                        GROUP BY r.post_id, p.id, p.kind, o.title_snapshot, pp.body
                        ORDER BY max(r.reported_week), r.post_id""")
                .query(ReportedPost.class)
                .list();
    }

    @Transactional
    public void closeReports(UUID postId, String upholdReason) {
        int closed = jdbc.sql("UPDATE anon.post_report SET status = :status WHERE post_id = :post AND status = 'open'")
                .param("status", upholdReason == null ? "dismissed" : "upheld")
                .param("post", postId)
                .update();
        if (closed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_moderated");
        }
        if (upholdReason != null) {
            jdbc.sql("UPDATE anon.post_version SET status = 'rejected', reason_code = :reason WHERE post_id = :post AND status = 'approved'")
                    .param("reason", upholdReason)
                    .param("post", postId)
                    .update();
        }
    }
}
