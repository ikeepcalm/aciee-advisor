package ro.aciee.advisor.domain.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.group.model.CreatedInvite;
import ro.aciee.advisor.domain.group.model.GroupSummary;
import ro.aciee.advisor.domain.group.model.Invite;
import ro.aciee.advisor.domain.group.model.InvitePreview;
import ro.aciee.advisor.domain.group.model.JoinRequest;
import ro.aciee.advisor.domain.group.model.Membership;
import ro.aciee.advisor.domain.group.model.MyGroups;
import ro.aciee.advisor.domain.group.model.Occupancy;
import ro.aciee.advisor.domain.group.model.PendingRequests;
import ro.aciee.advisor.domain.group.model.RosterEntry;
import ro.aciee.advisor.domain.group.model.Subgroup;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final String SELECT_GROUP = "SELECT g.id, g.code, g.specialization, g.study_year, g.academic_year FROM core.group_v g ";

    private static final String SELECT_REQUEST = """
            SELECT r.id, a.email, g.code AS group_code, g.academic_year, r.kind, s.label AS subgroup, r.status
            FROM core.membership_request r
            JOIN core.user_account a ON a.id = r.user_id
            JOIN core.group_v g ON g.id = r.group_id
            LEFT JOIN core.subgroup s ON s.id = r.subgroup_id
            """;

    private final JdbcClient jdbc;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public List<GroupSummary> currentGroups() {
        return jdbc.sql(SELECT_GROUP + "WHERE g.year_during @> current_date ORDER BY g.code")
                .query(GroupSummary.class)
                .list();
    }

    @Transactional(readOnly = true)
    public MyGroups mine() {
        var memberships = jdbc.sql("""
                        SELECT m.group_id, g.code AS group_code, g.specialization, g.study_year, g.academic_year,
                               s.label AS subgroup, lower(m.valid_during) AS since, upper(m.valid_during) AS until,
                               EXISTS (SELECT 1 FROM core.group_leader l WHERE l.membership_id = m.id) AS leader
                        FROM core.group_membership m
                        JOIN core.group_v g ON g.id = m.group_id
                        LEFT JOIN core.subgroup s ON s.id = m.subgroup_id
                        WHERE m.user_id = core.app_user_id()
                        ORDER BY lower(m.valid_during) DESC""")
                .query(Membership.class)
                .list();
        var requests = jdbc.sql(SELECT_REQUEST + "WHERE r.user_id = core.app_user_id() ORDER BY r.id DESC")
                .query(JoinRequest.class)
                .list();
        return new MyGroups(memberships, requests);
    }

    @Transactional(readOnly = true)
    public InvitePreview previewInvite(String token) {
        var group = jdbc.sql(SELECT_GROUP + """
                        JOIN core.group_invite i ON i.group_id = g.id
                        WHERE i.token_hash = sha256(convert_to(:token, 'UTF8')) AND i.expires_at > now()""")
                .param("token", token)
                .query(GroupSummary.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "invite_expired"));
        var subgroups = jdbc.sql("SELECT id, label FROM core.subgroup WHERE group_id = :id ORDER BY label")
                .param("id", group.getId())
                .query(Subgroup.class)
                .list();
        return new InvitePreview(group, subgroups);
    }

    @Transactional
    public void join(String token, Integer subgroupId) {
        var invite = jdbc.sql("""
                        SELECT id, group_id, max_uses, expires_at > now() AS live
                        FROM core.group_invite WHERE token_hash = sha256(convert_to(:token, 'UTF8'))
                        FOR NO KEY UPDATE""")
                .param("token", token)
                .query()
                .listOfRows();
        if (invite.isEmpty() || !(Boolean) invite.getFirst().get("live")) {
            throw new ResponseStatusException(HttpStatus.GONE, "invite_expired");
        }
        long uses = jdbc.sql("SELECT core.invite_uses(:id)")
                .param("id", invite.getFirst().get("id"))
                .query(Long.class)
                .single();
        if (uses >= ((Number) invite.getFirst().get("max_uses")).intValue()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invite_used_up");
        }
        try {
            jdbc.sql("""
                            INSERT INTO core.membership_request (user_id, group_id, subgroup_id, kind, invite_id)
                            VALUES (core.app_user_id(), :group, :subgroup, 'regular', :invite)""")
                    .param("group", invite.getFirst().get("group_id"))
                    .param("subgroup", subgroupId)
                    .param("invite", invite.getFirst().get("id"))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "request_pending");
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_subgroup");
        }
    }

    @Transactional
    public void claimLeadership(int groupId) {
        try {
            jdbc.sql("INSERT INTO core.group_leader_claim (user_id, group_id) VALUES (core.app_user_id(), :group)")
                    .param("group", groupId)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "claim_pending");
        }
        jdbc.sql("""
                        INSERT INTO core.membership_request (user_id, group_id, kind)
                        SELECT core.app_user_id(), :group, 'regular'
                        WHERE NOT EXISTS (SELECT 1 FROM core.group_membership m
                                          WHERE m.user_id = core.app_user_id() AND m.group_id = :group
                                            AND m.valid_during @> current_date)
                        ON CONFLICT (user_id, group_id) WHERE status = 'pending' DO NOTHING""")
                .param("group", groupId)
                .update();
    }

    @Transactional(readOnly = true)
    public PendingRequests pendingRequests(int groupId) {
        requireLeader(groupId);
        var requests = jdbc.sql(SELECT_REQUEST + "WHERE r.group_id = :group AND r.status = 'pending' ORDER BY r.id")
                .param("group", groupId)
                .query(JoinRequest.class)
                .list();
        var occupancy = occupancy(groupId);
        return new PendingRequests(requests, occupancy.getMembers(), occupancy.getMaxSize());
    }

    @Transactional(readOnly = true)
    public List<RosterEntry> roster(int groupId) {
        requireLeader(groupId);
        return jdbc.sql("""
                        SELECT a.email, s.label AS subgroup, lower(m.valid_during) AS since,
                               EXISTS (SELECT 1 FROM core.group_leader l WHERE l.membership_id = m.id) AS leader
                        FROM core.group_membership m
                        JOIN core.user_account a ON a.id = m.user_id
                        LEFT JOIN core.subgroup s ON s.id = m.subgroup_id
                        WHERE m.group_id = :group AND m.valid_during @> current_date
                        ORDER BY s.label NULLS LAST, a.email""")
                .param("group", groupId)
                .query(RosterEntry.class)
                .list();
    }

    @Transactional
    public void approve(int requestId) {
        var request = jdbc.sql("""
                        SELECT g.id, upper(ay.during) > current_date AS year_open
                        FROM core.study_group g
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        JOIN core.membership_request r ON r.group_id = g.id
                        WHERE r.id = :id AND r.status = 'pending'
                        FOR NO KEY UPDATE OF g""")
                .param("id", requestId)
                .query()
                .listOfRows();
        if (request.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_decided");
        }
        int group = (Integer) request.getFirst().get("id");
        requireLeader(group);
        if (!(Boolean) request.getFirst().get("year_open")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "year_ended");
        }
        var occupancy = occupancy(group);
        if (occupancy.getMembers() >= occupancy.getMaxSize()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "group_full");
        }
        try {
            int approved = jdbc.sql("""
                            WITH r AS (
                                UPDATE core.membership_request
                                SET status = 'approved', decided_by = core.app_user_id(), decided_at = now()
                                WHERE id = :id AND status = 'pending'
                                RETURNING id, user_id, group_id, subgroup_id, kind)
                            INSERT INTO core.group_membership (user_id, group_id, subgroup_id, kind, source, request_id, valid_during)
                            SELECT r.user_id, r.group_id, r.subgroup_id, r.kind, 'request', r.id,
                                   daterange(greatest(current_date, lower(ay.during)), upper(ay.during))
                            FROM r
                            JOIN core.study_group g ON g.id = r.group_id
                            JOIN core.academic_year ay ON ay.id = g.academic_year_id""")
                    .param("id", requestId)
                    .update();
            if (approved == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "already_decided");
            }
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_member");
        }
    }

    @Transactional
    public void reject(int requestId) {
        jdbc.sql("SELECT group_id FROM core.membership_request WHERE id = :id")
                .param("id", requestId)
                .query(Integer.class)
                .optional()
                .ifPresent(this::requireLeader);
        int updated = jdbc.sql("""
                        UPDATE core.membership_request
                        SET status = 'rejected', decided_by = core.app_user_id(), decided_at = now()
                        WHERE id = :id AND status = 'pending'""")
                .param("id", requestId)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_decided");
        }
    }

    @Transactional(readOnly = true)
    public List<Invite> invites(int groupId) {
        requireLeader(groupId);
        return jdbc.sql("""
                        SELECT id, expires_at, max_uses, core.invite_uses(id) AS uses
                        FROM core.group_invite
                        WHERE group_id = :group AND expires_at > now()
                        ORDER BY expires_at""")
                .param("group", groupId)
                .query(Invite.class)
                .list();
    }

    @Transactional
    public CreatedInvite createInvite(int groupId, int days, int maxUses) {
        requireLeader(groupId);
        var bytes = new byte[24];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expiresAt = OffsetDateTime.now().plusDays(days);
        int id = jdbc.sql("""
                        INSERT INTO core.group_invite (group_id, token_hash, expires_at, max_uses, created_by)
                        VALUES (:group, sha256(convert_to(:token, 'UTF8')), :expiresAt, :maxUses, core.app_user_id())
                        RETURNING id""")
                .param("group", groupId)
                .param("token", token)
                .param("expiresAt", expiresAt)
                .param("maxUses", maxUses)
                .query(Integer.class)
                .single();
        return new CreatedInvite(id, token, expiresAt);
    }

    @Transactional
    public void deactivateInvite(int inviteId) {
        int updated = jdbc.sql("""
                        UPDATE core.group_invite SET expires_at = now()
                        WHERE id = :id AND expires_at > now() AND (core.leads_group(group_id) OR core.is_admin())""")
                .param("id", inviteId)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invite_not_found");
        }
    }

    private Occupancy occupancy(int groupId) {
        return jdbc.sql("""
                        SELECT g.max_size,
                               (SELECT count(*) FROM core.group_membership m
                                WHERE m.group_id = g.id AND m.valid_during @> current_date) AS members
                        FROM core.study_group g WHERE g.id = :group""")
                .param("group", groupId)
                .query(Occupancy.class)
                .single();
    }

    private void requireLeader(int groupId) {
        boolean allowed = jdbc.sql("SELECT core.leads_group(:group) OR core.is_admin()")
                .param("group", groupId)
                .query(Boolean.class)
                .single();
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not_leader");
        }
    }
}
