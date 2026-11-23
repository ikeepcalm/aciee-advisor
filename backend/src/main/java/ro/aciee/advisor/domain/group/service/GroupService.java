package ro.aciee.advisor.domain.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.catalog.model.Option;
import ro.aciee.advisor.domain.group.model.CreatedInvite;
import ro.aciee.advisor.domain.group.model.GroupSummary;
import ro.aciee.advisor.domain.group.model.GroupTeaching;
import ro.aciee.advisor.domain.group.model.Invite;
import ro.aciee.advisor.domain.group.model.InvitePreview;
import ro.aciee.advisor.domain.group.model.JoinRequest;
import ro.aciee.advisor.domain.group.model.Membership;
import ro.aciee.advisor.domain.group.model.MyGroups;
import ro.aciee.advisor.domain.group.model.NewProfessor;
import ro.aciee.advisor.domain.group.model.Occupancy;
import ro.aciee.advisor.domain.group.model.PendingRequests;
import ro.aciee.advisor.domain.group.model.ProfessorOption;
import ro.aciee.advisor.domain.group.model.RosterEntry;
import ro.aciee.advisor.domain.group.model.Subgroup;
import ro.aciee.advisor.domain.group.model.TeachingSlot;
import ro.aciee.advisor.domain.professor.service.ProfessorDirectory;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final String SELECT_GROUP = "SELECT g.id, g.code, g.specialization, g.study_year, g.academic_year FROM core.group_v g ";

    private static final String SELECT_REQUEST = """
            SELECT r.id, a.email, a.display_name, g.code AS group_code, g.academic_year, r.kind, s.label AS subgroup, r.status
            FROM core.membership_request r
            JOIN core.user_account a ON a.id = r.user_id
            JOIN core.group_v g ON g.id = r.group_id
            LEFT JOIN core.subgroup s ON s.id = r.subgroup_id
            """;

    private final JdbcClient jdbc;
    private final ProfessorDirectory professorDirectory;
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
                        SELECT i.id, i.group_id, i.max_uses, i.expires_at > now() AS live,
                               upper(ay.during) > current_date AS year_open,
                               EXISTS (SELECT 1 FROM core.group_membership m
                                       WHERE m.user_id = core.app_user_id() AND m.group_id = i.group_id
                                         AND m.valid_during && daterange(current_date, NULL)) AS member
                        FROM core.group_invite i
                        JOIN core.study_group g ON g.id = i.group_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE i.token_hash = sha256(convert_to(:token, 'UTF8'))
                        FOR NO KEY UPDATE OF i""")
                .param("token", token)
                .query()
                .listOfRows();
        if (invite.isEmpty() || !(Boolean) invite.getFirst().get("live")) {
            throw new ResponseStatusException(HttpStatus.GONE, "invite_expired");
        }
        var row = invite.getFirst();
        if (!(Boolean) row.get("year_open")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "year_ended");
        }
        if ((Boolean) row.get("member")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_member");
        }
        long uses = jdbc.sql("SELECT core.invite_uses(:id)")
                .param("id", row.get("id"))
                .query(Long.class)
                .single();
        if (uses >= ((Number) row.get("max_uses")).intValue()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invite_used_up");
        }
        try {
            jdbc.sql("""
                            INSERT INTO core.membership_request (user_id, group_id, subgroup_id, kind, invite_id)
                            VALUES (core.app_user_id(), :group, :subgroup, 'regular', :invite)""")
                    .param("group", row.get("group_id"))
                    .param("subgroup", subgroupId)
                    .param("invite", row.get("id"))
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
                                            AND m.valid_during && daterange(current_date, NULL))
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
                        SELECT a.email, a.display_name, s.label AS subgroup, lower(m.valid_during) AS since,
                               EXISTS (SELECT 1 FROM core.group_leader l WHERE l.membership_id = m.id) AS leader
                        FROM core.group_membership m
                        JOIN core.user_account a ON a.id = m.user_id
                        LEFT JOIN core.subgroup s ON s.id = m.subgroup_id
                        WHERE m.group_id = :group AND m.valid_during && daterange(current_date, NULL)
                        ORDER BY s.label NULLS LAST, coalesce(a.display_name, a.email)""")
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

    @Transactional(readOnly = true)
    public GroupTeaching teaching(int groupId) {
        requireLeader(groupId);
        var professors = jdbc.sql("""
                        SELECT p.id, t.label_ro || ' ' || p.full_name || ' · ' || d.name_ro AS label, p.full_name,
                               NOT p.name_confirmed AND EXISTS (
                                   SELECT 1 FROM core.teaching_assignment ta
                                   WHERE ta.professor_id = p.id AND ta.group_id = :group
                                     AND ta.valid_during && daterange(current_date, NULL)) AS renamable
                        FROM core.professor p
                        JOIN ref.academic_title t ON t.code = p.title_code
                        JOIN core.department d ON d.id = p.department_id
                        WHERE p.archived_at IS NULL
                        ORDER BY p.full_name, d.name_ro""")
                .param("group", groupId)
                .query(ProfessorOption.class)
                .list();
        var titles = jdbc.sql("SELECT code, label_ro AS label FROM ref.academic_title ORDER BY label_ro")
                .query(Option.class)
                .list();
        return new GroupTeaching(slots(groupId), professors, titles);
    }

    @Transactional
    public ProfessorOption addProfessor(int groupId, NewProfessor body) {
        requireLeader(groupId);
        String name = cleanName(body.getFullName());
        Integer existing = jdbc.sql("""
                        SELECT id FROM core.professor
                        WHERE archived_at IS NULL AND core.name_key(full_name) = core.name_key(:name)
                        ORDER BY id LIMIT 1""")
                .param("name", name)
                .query(Integer.class)
                .optional()
                .orElse(null);
        int id;
        try {
            id = existing != null ? existing : professorDirectory.create(name, body.getTitleCode());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_title");
        }
        return professorOption(groupId, id);
    }

    @Transactional
    public ProfessorOption renameProfessor(int groupId, int professorId, NewProfessor body) {
        requireLeader(groupId);
        int updated = jdbc.sql("""
                        UPDATE core.professor p SET full_name = :name
                        WHERE p.id = :professor AND NOT p.name_confirmed AND p.archived_at IS NULL
                          AND EXISTS (SELECT 1 FROM core.teaching_assignment ta
                                      WHERE ta.professor_id = p.id AND ta.group_id = :group
                                        AND ta.valid_during && daterange(current_date, NULL))""")
                .param("name", cleanName(body.getFullName()))
                .param("professor", professorId)
                .param("group", groupId)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not_renamable");
        }
        return professorOption(groupId, professorId);
    }

    private ProfessorOption professorOption(int groupId, int professorId) {
        return teaching(groupId).getProfessors().stream().filter(p -> p.getId() == professorId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    }

    private static String cleanName(String name) {
        String cleaned = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (cleaned.length() < 3 || cleaned.length() > 200 || !cleaned.matches("[\\p{L} .'-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_name");
        }
        return cleaned;
    }

    @Transactional
    public void assignTeachers(int groupId, List<TeachingSlot> changes) {
        requireLeader(groupId);
        boolean yearOpen = jdbc.sql("""
                        SELECT upper(ay.during) > current_date
                        FROM core.study_group g JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE g.id = :group FOR NO KEY UPDATE OF g""")
                .param("group", groupId)
                .query(Boolean.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        if (!yearOpen) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "year_ended");
        }
        var current = slots(groupId).stream().collect(Collectors.toMap(TeachingSlot::key, Function.identity()));
        for (var change : changes) {
            var slot = current.get(change.key());
            if (slot == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_slot");
            }
            if (!Objects.equals(slot.getProfessorId(), change.getProfessorId())) {
                endAssignment(change);
                if (change.getProfessorId() != null) {
                    startAssignment(groupId, change);
                }
            }
        }
    }

    private List<TeachingSlot> slots(int groupId) {
        return jdbc.sql("""
                        SELECT o.id AS offering_id, o.title_snapshot AS title, ci.semester_no, c.component,
                               s.id AS subgroup_id, s.label AS subgroup,
                               (SELECT ta.professor_id FROM core.teaching_assignment ta
                                WHERE ta.offering_id = o.id AND ta.component = c.component
                                  AND ta.subgroup_id IS NOT DISTINCT FROM s.id
                                  AND ta.valid_during && daterange(current_date, NULL)
                                ORDER BY lower(ta.valid_during) DESC LIMIT 1) AS professor_id
                        FROM core.course_offering o
                        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
                        CROSS JOIN LATERAL (VALUES (1, 'lecture', ci.lecture_hours), (2, 'seminar', ci.seminar_hours),
                                                   (3, 'lab', ci.lab_hours), (4, 'project', ci.project_hours)) c(no, component, hours)
                        CROSS JOIN LATERAL (SELECT NULL::integer AS id, NULL AS label
                                            UNION ALL
                                            SELECT id, label FROM core.subgroup WHERE group_id = o.group_id) s
                        WHERE o.group_id = :group AND c.hours > 0 AND (c.component <> 'lecture' OR s.id IS NULL)
                        ORDER BY ci.semester_no, o.title_snapshot, c.no, s.label NULLS FIRST""")
                .param("group", groupId)
                .query(TeachingSlot.class)
                .list();
    }

    private void endAssignment(TeachingSlot slot) {
        var match = """
                offering_id = :offeringId AND component = :component
                  AND subgroup_id IS NOT DISTINCT FROM CAST(:subgroupId AS integer)
                  AND valid_during && daterange(current_date, NULL)""";
        jdbc.sql("DELETE FROM core.teaching_assignment WHERE lower(valid_during) >= current_date AND " + match)
                .paramSource(slot)
                .update();
        jdbc.sql("UPDATE core.teaching_assignment SET valid_during = daterange(lower(valid_during), current_date) WHERE " + match)
                .paramSource(slot)
                .update();
    }

    private void startAssignment(int groupId, TeachingSlot slot) {
        int inserted = jdbc.sql("""
                        INSERT INTO core.teaching_assignment (offering_id, group_id, professor_id, component, subgroup_id, valid_during)
                        SELECT :offering, g.id, p.id, :component, :subgroup,
                               daterange(greatest(current_date, lower(ay.during)), upper(ay.during))
                        FROM core.study_group g
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        JOIN core.professor p ON p.id = :professor AND p.archived_at IS NULL
                        WHERE g.id = :group""")
                .param("offering", slot.getOfferingId())
                .param("component", slot.getComponent())
                .param("subgroup", slot.getSubgroupId())
                .param("professor", slot.getProfessorId())
                .param("group", groupId)
                .update();
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_professor");
        }
    }

    private Occupancy occupancy(int groupId) {
        return jdbc.sql("""
                        SELECT g.max_size,
                               (SELECT count(*) FROM core.group_membership m
                                WHERE m.group_id = g.id AND m.valid_during && daterange(current_date, NULL)) AS members
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
