package ro.aciee.advisor.domain.leadership.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.group.service.GroupService;
import ro.aciee.advisor.domain.leadership.model.Leader;
import ro.aciee.advisor.domain.leadership.model.LeaderClaim;
import ro.aciee.advisor.domain.leadership.model.Leadership;

@Service
@RequiredArgsConstructor
public class LeadershipService {

    private final JdbcClient jdbc;
    private final GroupService groupService;

    @Transactional(readOnly = true)
    public Leadership overview() {
        var claims = jdbc.sql("""
                        SELECT c.id, a.email, g.code AS group_code, g.academic_year, lower(m.valid_during) AS member_since
                        FROM core.group_leader_claim c
                        JOIN core.user_account a ON a.id = c.user_id
                        JOIN core.group_v g ON g.id = c.group_id
                        LEFT JOIN core.group_membership m
                            ON m.user_id = c.user_id AND m.group_id = c.group_id AND m.valid_during @> current_date
                        WHERE c.status = 'pending'
                        ORDER BY c.id""")
                .query(LeaderClaim.class)
                .list();
        var leaders = jdbc.sql("""
                        SELECT l.membership_id, a.email, g.code AS group_code, g.academic_year
                        FROM core.group_leader l
                        JOIN core.group_membership m ON m.id = l.membership_id
                        JOIN core.user_account a ON a.id = m.user_id
                        JOIN core.group_v g ON g.id = m.group_id
                        WHERE m.valid_during @> current_date
                        ORDER BY g.code""")
                .query(Leader.class)
                .list();
        return new Leadership(claims, leaders);
    }

    @Transactional
    public void approve(int claimId) {
        var claim = jdbc.sql("""
                        UPDATE core.group_leader_claim
                        SET status = 'approved', decided_by = core.app_user_id(), decided_at = now()
                        WHERE id = :id AND status = 'pending'
                        RETURNING user_id, group_id""")
                .param("id", claimId)
                .query()
                .listOfRows();
        if (claim.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_decided");
        }
        var userId = claim.getFirst().get("user_id");
        var groupId = claim.getFirst().get("group_id");
        jdbc.sql("""
                        SELECT id FROM core.membership_request
                        WHERE user_id = :user AND group_id = :group AND status = 'pending' AND offering_id IS NULL""")
                .param("user", userId)
                .param("group", groupId)
                .query(Integer.class)
                .optional()
                .ifPresent(groupService::approve);
        try {
            int inserted = jdbc.sql("""
                            INSERT INTO core.group_leader (membership_id, claim_id)
                            SELECT m.id, :claim FROM core.group_membership m
                            WHERE m.user_id = :user AND m.group_id = :group AND m.valid_during @> current_date""")
                    .param("claim", claimId)
                    .param("user", userId)
                    .param("group", groupId)
                    .update();
            if (inserted == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "not_member");
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_leader");
        }
    }

    @Transactional
    public void reject(int claimId) {
        int updated = jdbc.sql("""
                        UPDATE core.group_leader_claim
                        SET status = 'rejected', decided_by = core.app_user_id(), decided_at = now()
                        WHERE id = :id AND status = 'pending'""")
                .param("id", claimId)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_decided");
        }
    }

    @Transactional
    public void revoke(int membershipId) {
        int deleted = jdbc.sql("DELETE FROM core.group_leader WHERE membership_id = :id")
                .param("id", membershipId)
                .update();
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "leader_not_found");
        }
    }
}
