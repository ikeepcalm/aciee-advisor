package ro.aciee.advisor.domain.submission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.submission.model.AssignmentOption;
import ro.aciee.advisor.domain.submission.model.OfferingForm;
import ro.aciee.advisor.domain.submission.model.PostState;
import ro.aciee.advisor.domain.submission.model.Submission;
import ro.aciee.advisor.domain.submission.model.SubmissionBody;
import ro.aciee.advisor.domain.submission.model.TeacherRating;
import ro.aciee.advisor.domain.token.service.BlindRsa;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final List<String> AXES = IntStream.rangeClosed(1, 14).mapToObj("a%02d"::formatted).toList();

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public OfferingForm form(int offeringId) {
        var form = jdbc.sql("""
                        SELECT o.offering_id, o.title_snapshot AS title, g.academic_year, o.semester_no
                        FROM core.offering_v o JOIN core.group_v g ON g.id = o.group_id
                        WHERE o.offering_id = :offering""")
                .param("offering", offeringId)
                .query(OfferingForm.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        form.setAssignments(jdbc.sql("""
                        SELECT DISTINCT ON (ta.component, sg.label, p.id)
                               ta.id, ta.component, c.label_ro AS component_label, p.full_name AS professor,
                               t.label_ro AS title, sg.label AS subgroup
                        FROM core.teaching_assignment ta
                        JOIN core.professor p ON p.id = ta.professor_id
                        JOIN ref.academic_title t ON t.code = p.title_code
                        JOIN ref.component c ON c.code = ta.component
                        LEFT JOIN core.subgroup sg ON sg.id = ta.subgroup_id
                        WHERE ta.offering_id = :offering
                        ORDER BY ta.component, sg.label, p.id, upper(ta.valid_during) DESC NULLS FIRST""")
                .param("offering", offeringId)
                .query(AssignmentOption.class)
                .list());
        return form;
    }

    @Transactional
    public void create(SubmissionBody body) {
        if (body.getOfferingId() == null || body.getFingerprint() == null || body.getMsg() == null || body.getSig() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_token");
        }
        byte[] publicKey = jdbc.sql("SELECT public_key FROM anon.offering_key WHERE fingerprint = :fingerprint AND offering_id = :offering")
                .param("fingerprint", body.getFingerprint())
                .param("offering", body.getOfferingId())
                .query(byte[].class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_token"));
        if (!BlindRsa.verify(rsaKey(publicKey), body.getMsg(), body.getSig())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_token");
        }
        byte[] keyHash = sha256(body.getMsg());
        int inserted = jdbc.sql("""
                        INSERT INTO anon.submission (key_hash, offering_id, key_fingerprint, exam_difficulty, labs_help,
                                                     attendance_useful, weekly_hours, exam_format, attendance_strictness)
                        VALUES (:keyHash, :offering, :fingerprint, :examDifficulty, :labsHelp, :attendanceUseful, :weeklyHours,
                                :examFormat, :attendanceStrictness)
                        ON CONFLICT (key_hash) DO NOTHING""")
                .paramSource(survival(body)
                        .addValue("keyHash", keyHash)
                        .addValue("offering", body.getOfferingId())
                        .addValue("fingerprint", body.getFingerprint()))
                .update();
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_submitted");
        }
        writeDetails(keyHash, body.getOfferingId(), body);
    }

    @Transactional
    public void update(int offeringId, byte[] msg, SubmissionBody body) {
        byte[] keyHash = sha256(msg);
        int updated = jdbc.sql("""
                        UPDATE anon.submission
                        SET exam_difficulty = :examDifficulty, labs_help = :labsHelp, attendance_useful = :attendanceUseful,
                            weekly_hours = :weeklyHours, exam_format = :examFormat, attendance_strictness = :attendanceStrictness
                        WHERE key_hash = :keyHash AND offering_id = :offering""")
                .paramSource(survival(body).addValue("keyHash", keyHash).addValue("offering", offeringId))
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission_not_found");
        }
        jdbc.sql("DELETE FROM anon.rating WHERE key_hash = :keyHash").param("keyHash", keyHash).update();
        writeDetails(keyHash, offeringId, body);
    }

    @Transactional
    public void delete(int offeringId, byte[] msg) {
        int deleted = jdbc.sql("DELETE FROM anon.submission WHERE key_hash = :keyHash AND offering_id = :offering")
                .param("keyHash", sha256(msg))
                .param("offering", offeringId)
                .update();
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission_not_found");
        }
    }

    @Transactional(readOnly = true)
    public Submission find(int offeringId, byte[] msg) {
        byte[] keyHash = sha256(msg);
        var submission = jdbc.sql("""
                        SELECT offering_id, exam_difficulty, labs_help, attendance_strictness, attendance_useful, exam_format, weekly_hours
                        FROM anon.submission WHERE key_hash = :keyHash AND offering_id = :offering""")
                .param("keyHash", keyHash)
                .param("offering", offeringId)
                .query(Submission.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "submission_not_found"));
        submission.setRatings(jdbc.sql("SELECT teaching_assignment_id, ARRAY[%s]::smallint[] AS scores FROM anon.rating WHERE key_hash = :keyHash"
                        .formatted(String.join(", ", AXES)))
                .param("keyHash", keyHash)
                .query((rs, row) -> new TeacherRating(rs.getInt("teaching_assignment_id"),
                        Arrays.stream((Object[]) rs.getArray("scores").getArray())
                                .map(score -> score == null ? null : ((Number) score).intValue()).toList(), null))
                .list());
        submission.setPosts(jdbc.sql("""
                        SELECT p.id, p.kind, p.teaching_assignment_id, v.body, v.status, r.label_ro AS reason,
                               EXISTS (SELECT 1 FROM anon.published_post pp WHERE pp.id = p.id) AS published
                        FROM anon.post p
                        JOIN LATERAL (SELECT * FROM anon.post_version v WHERE v.post_id = p.id ORDER BY v.version_no DESC LIMIT 1) v ON true
                        LEFT JOIN ref.moderation_reason r ON r.code = v.reason_code
                        WHERE p.key_hash = :keyHash""")
                .param("keyHash", keyHash)
                .query(PostState.class)
                .list());
        return submission;
    }

    @Transactional
    public void report(UUID postId, String reasonCode) {
        jdbc.sql("""
                        INSERT INTO anon.post_report (post_id, reason_code, reported_week)
                        VALUES (:post, :reason, date_trunc('week', current_date)::date)""")
                .param("post", postId)
                .param("reason", reasonCode)
                .update();
    }

    private void writeDetails(byte[] keyHash, int offeringId, SubmissionBody body) {
        var columns = String.join(", ", AXES);
        var values = AXES.stream().map(axis -> ":" + axis).collect(Collectors.joining(", "));
        for (var rating : body.getRatings().stream().filter(TeacherRating::scored).toList()) {
            var statement = jdbc.sql("INSERT INTO anon.rating (key_hash, teaching_assignment_id, offering_id, %s) VALUES (:keyHash, :assignment, :offering, %s)"
                            .formatted(columns, values))
                    .param("keyHash", keyHash)
                    .param("assignment", rating.getTeachingAssignmentId())
                    .param("offering", offeringId);
            for (int i = 0; i < AXES.size(); i++) {
                statement = statement.param(AXES.get(i), rating.getScores().get(i), Types.SMALLINT);
            }
            statement.update();
        }
        writePost(keyHash, offeringId, null, body.getTip());
        body.getRatings().forEach(rating -> writePost(keyHash, offeringId, rating.getTeachingAssignmentId(), rating.getComment()));
    }

    private void writePost(byte[] keyHash, int offeringId, Integer assignmentId, String text) {
        String kind = assignmentId == null ? "tip" : "comment";
        String body = text == null ? "" : text.strip();
        var postId = jdbc.sql("SELECT id FROM anon.post WHERE key_hash = :keyHash AND kind = :kind AND teaching_assignment_id IS NOT DISTINCT FROM :assignment")
                .param("keyHash", keyHash)
                .param("kind", kind)
                .param("assignment", assignmentId, Types.INTEGER)
                .query(UUID.class)
                .optional();
        if (body.isEmpty()) {
            postId.ifPresent(id -> jdbc.sql("DELETE FROM anon.post WHERE id = :id").param("id", id).update());
            return;
        }
        var id = postId.orElseGet(() -> jdbc.sql("""
                        INSERT INTO anon.post (key_hash, offering_id, kind, teaching_assignment_id)
                        VALUES (:keyHash, :offering, :kind, :assignment) RETURNING id""")
                .param("keyHash", keyHash)
                .param("offering", offeringId)
                .param("kind", kind)
                .param("assignment", assignmentId, Types.INTEGER)
                .query(UUID.class)
                .single());
        jdbc.sql("""
                        INSERT INTO anon.post_version (post_id, version_no, kind, body, submitted_week)
                        SELECT :id, coalesce(max(version_no), 0) + 1, :kind, :body, date_trunc('week', current_date)::date
                        FROM anon.post_version WHERE post_id = :id
                        HAVING (SELECT body FROM anon.post_version WHERE post_id = :id ORDER BY version_no DESC LIMIT 1) IS DISTINCT FROM :body""")
                .param("id", id)
                .param("kind", kind)
                .param("body", body)
                .update();
    }

    private static MapSqlParameterSource survival(SubmissionBody body) {
        return new MapSqlParameterSource()
                .addValue("examDifficulty", body.getExamDifficulty(), Types.SMALLINT)
                .addValue("labsHelp", body.getLabsHelp(), Types.SMALLINT)
                .addValue("attendanceUseful", body.getAttendanceUseful(), Types.VARCHAR)
                .addValue("weeklyHours", body.getWeeklyHours(), Types.SMALLINT)
                .addValue("examFormat", body.getExamFormat(), Types.VARCHAR)
                .addValue("attendanceStrictness", body.getAttendanceStrictness(), Types.SMALLINT);
    }

    private static RSAPublicKey rsaKey(byte[] spki) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
        } catch (GeneralSecurityException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_token");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
