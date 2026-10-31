package ro.aciee.advisor.domain.group;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ro.aciee.advisor.TestData;
import ro.aciee.advisor.TestcontainersConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GroupFlowTests {

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    int groupId;
    int subgroupId;
    int adminId;
    String prefix;

    @BeforeEach
    void seed() {
        prefix = "g" + RUN.incrementAndGet();
        groupId = TestData.currentGroup(jdbc, prefix, 2);
        subgroupId = insert("INSERT INTO core.subgroup (group_id, label) VALUES (%d, '1') RETURNING id".formatted(groupId));
        adminId = account("admin");
        jdbc.sql("INSERT INTO core.user_role (user_id, role_code) VALUES (:id, 'admin')").param("id", adminId).update();
    }

    @Test
    void leaderIsApprovedThenApprovesAJoinRequest() throws Exception {
        int leaderId = account("leader");
        int studentId = account("student");

        send(post("/api/groups/%d/leader-claims".formatted(groupId)), leaderId).andExpect(status().isAccepted());
        send(post("/api/groups/%d/leader-claims".formatted(groupId)), leaderId).andExpect(status().isConflict());
        send(get("/api/admin/leaders"), leaderId).andExpect(status().isForbidden());
        send(get("/api/admin/leaders"), adminId).andExpect(status().isOk());
        approveClaim(groupId, prefix);
        send(get("/api/groups/mine"), leaderId).andExpect(jsonPath("$.memberships[0].leader").value(true));

        String token = JsonPath.read(send(post("/api/groups/%d/invites".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":7,\"maxUses\":5}"), leaderId)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.token");
        send(post("/api/groups/%d/invites".formatted(groupId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":7,\"maxUses\":5}"), studentId)
                .andExpect(status().isForbidden());

        send(get("/api/groups/invites/" + token), studentId).andExpect(jsonPath("$.subgroups[0].label").value("1"));
        join(token, studentId).andExpect(status().isAccepted());
        join(token, studentId).andExpect(jsonPath("$.detail").value("request_pending"));

        int requestId = idOf(send(get("/api/groups/%d/requests".formatted(groupId)), leaderId), "$.requests[0].id");
        send(post("/api/groups/requests/%d/approve".formatted(requestId)), studentId).andExpect(status().isForbidden());
        send(post("/api/groups/requests/%d/reject".formatted(requestId)), studentId).andExpect(status().isForbidden());
        send(post("/api/groups/requests/%d/approve".formatted(requestId)), leaderId).andExpect(status().isNoContent());
        send(post("/api/groups/requests/%d/approve".formatted(requestId)), leaderId)
                .andExpect(jsonPath("$.detail").value("already_decided"));
        join(token, studentId).andExpect(jsonPath("$.detail").value("already_member"));

        send(get("/api/groups/mine"), studentId)
                .andExpect(jsonPath("$.memberships[0].subgroup").value("1"))
                .andExpect(jsonPath("$.memberships[0].leader").value(false));
        send(get("/api/groups/%d/members".formatted(groupId)), leaderId).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void fullGroupAndUsedUpInviteAreRefused() throws Exception {
        int leaderId = account("leader");
        becomeLeader(leaderId, groupId, prefix);
        String token = JsonPath.read(send(post("/api/groups/%d/invites".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":1,\"maxUses\":2}"), leaderId)
                .andReturn().getResponse().getContentAsString(), "$.token");

        int first = account("first");
        int second = account("second");
        join(token, first).andExpect(status().isAccepted());
        join(token, second).andExpect(status().isAccepted());
        join(token, account("third")).andExpect(jsonPath("$.detail").value("invite_used_up"));

        var pending = send(get("/api/groups/%d/requests".formatted(groupId)), leaderId).andReturn().getResponse().getContentAsString();
        int firstRequest = JsonPath.<Integer>read(pending, "$.requests[0].id");
        int secondRequest = JsonPath.<Integer>read(pending, "$.requests[1].id");
        send(post("/api/groups/requests/%d/approve".formatted(firstRequest)), leaderId).andExpect(status().isNoContent());
        send(post("/api/groups/requests/%d/approve".formatted(secondRequest)), leaderId)
                .andExpect(jsonPath("$.detail").value("group_full"));
    }

    @Test
    void leaderMapsAnAssistantToEachSubgroup() throws Exception {
        int leaderId = account("leader");
        becomeLeader(leaderId, groupId, prefix);
        int departmentId = insert("""
                INSERT INTO core.department (faculty_id, name_ro)
                SELECT id, 'Calculatoare' FROM core.faculty WHERE code = '%s' RETURNING id""".formatted(prefix));
        int first = insert(professor("Maria Stan", departmentId));
        int second = insert(professor("Andrei Ionescu", departmentId));
        int subjectId = insert("INSERT INTO core.subject (code, name_ro) VALUES ('%s', 'Baze de date') RETURNING id".formatted(prefix));
        int itemId = insert("""
                INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no, lecture_hours, lab_hours)
                SELECT plan_id, %d, 3, 1, 2, 2 FROM core.study_group WHERE id = %d RETURNING id""".formatted(subjectId, groupId));
        int offeringId = insert("""
                INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
                SELECT %d, id, plan_id, study_year, 'Baze de date' FROM core.study_group WHERE id = %d RETURNING id"""
                .formatted(itemId, groupId));

        send(get("/api/groups/%d/teaching".formatted(groupId)), leaderId)
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].subgroup").doesNotExist())
                .andExpect(jsonPath("$.slots[1].component").value("lab"))
                .andExpect(jsonPath("$.slots[1].subgroup").value("1"))
                .andExpect(jsonPath("$.slots[1].professorId").doesNotExist());
        assign(offeringId, "lab", first, leaderId).andExpect(status().isNoContent());
        assign(offeringId, "lab", second, leaderId).andExpect(status().isNoContent());
        send(get("/api/groups/%d/teaching".formatted(groupId)), leaderId).andExpect(jsonPath("$.slots[1].professorId").value(second));
        assertThat(jdbc.sql("SELECT count(*) FROM core.teaching_assignment WHERE offering_id = :id").param("id", offeringId)
                .query(Long.class).single()).isEqualTo(1);

        assign(offeringId, "lecture", first, leaderId).andExpect(jsonPath("$.detail").value("invalid_slot"));
        assign(offeringId, "lab", first, account("student")).andExpect(status().isForbidden());
    }

    @Test
    void rolloverMovesAGroupToTheNextYearForItsMembersToReconfirm() throws Exception {
        String code = "13r" + prefix;
        int oldGroup = TestData.currentGroup(jdbc, code, 30);
        insert("INSERT INTO core.subgroup (group_id, label) VALUES (%d, '1') RETURNING id".formatted(oldGroup));
        int subjectId = insert("INSERT INTO core.subject (code, name_ro) VALUES ('%s', 'Proiect') RETURNING id".formatted(code));
        insert("""
                INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no)
                SELECT plan_id, %d, 4, 1 FROM core.study_group WHERE id = %d RETURNING id""".formatted(subjectId, oldGroup));
        int leaderId = account("leader");
        becomeLeader(leaderId, oldGroup, code);
        int yearId = insert("SELECT academic_year_id FROM core.study_group WHERE id = %d".formatted(oldGroup));

        String rollover = "/api/admin/tables/academic_year/%d/rollover".formatted(yearId);
        if (jdbc.sql("SELECT count(*) FROM core.academic_year").query(Long.class).single() == 1) {
            send(post(rollover), adminId).andExpect(jsonPath("$.detail").value("next_year_missing"));
        }
        jdbc.sql("""
                INSERT INTO core.academic_year (during)
                SELECT daterange(upper(during), upper(during) + 365) FROM core.academic_year WHERE id = :year
                ON CONFLICT DO NOTHING""").param("year", yearId).update();
        send(post(rollover), adminId).andExpect(status().isOk());
        send(post(rollover), adminId).andExpect(jsonPath("$.created").value(0));

        int newGroup = insert("SELECT id FROM core.study_group WHERE code = '14r%s' AND academic_year_id <> %d".formatted(prefix, yearId));
        var moved = jdbc.sql("""
                        SELECT g.study_year || ' ' || (SELECT count(*) FROM core.subgroup s WHERE s.group_id = g.id)
                               || ' ' || (SELECT count(*) FROM core.course_offering o WHERE o.group_id = g.id)
                               || ' ' || (SELECT string_agg(c.status, ',') FROM core.group_leader_claim c
                                          WHERE c.group_id = g.id AND c.user_id = :leader)
                        FROM core.study_group g WHERE g.id = :group""")
                .param("leader", leaderId)
                .param("group", newGroup)
                .query(String.class)
                .single();
        assertThat(moved).isEqualTo("4 1 1 pending");
        send(get("/api/groups/%d/requests".formatted(newGroup)), adminId)
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].email").value("leader.%s@student.ugal.ro".formatted(prefix)));

        approveClaim(newGroup, "14r" + prefix);
        send(get("/api/groups/%d/requests".formatted(newGroup)), leaderId)
                .andExpect(jsonPath("$.requests.length()").value(0))
                .andExpect(jsonPath("$.members").value(1));
    }

    private void becomeLeader(int userId, int group, String code) throws Exception {
        send(post("/api/groups/%d/leader-claims".formatted(group)), userId).andExpect(status().isAccepted());
        approveClaim(group, code);
    }

    private void approveClaim(int group, String code) throws Exception {
        int claimId = idOf(send(get("/api/admin/leaders"), adminId), "$.claims[?(@.groupCode == '%s')].id".formatted(code));
        send(post("/api/admin/leaders/claims/%d/approve".formatted(claimId)), adminId).andExpect(status().isNoContent());
    }

    private ResultActions assign(int offeringId, String component, int professorId, int userId) throws Exception {
        return send(put("/api/groups/%d/teaching".formatted(groupId)).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"offeringId\":%d,\"component\":\"%s\",\"subgroupId\":%d,\"professorId\":%d}]"
                        .formatted(offeringId, component, subgroupId, professorId)), userId);
    }

    private String professor(String name, int departmentId) {
        return """
                INSERT INTO core.professor (full_name, title_code, department_id, slug)
                VALUES ('%s', 'asist', %d, '%s') RETURNING id"""
                .formatted(name, departmentId, (name + "-" + prefix).toLowerCase().replace(' ', '-'));
    }

    private ResultActions join(String token, int userId) throws Exception {
        return send(post("/api/groups/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"subgroupId\":%d}".formatted(token, subgroupId)), userId);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, int userId) throws Exception {
        boolean admin = userId == adminId;
        var authorities = admin ? AuthorityUtils.createAuthorityList("ROLE_STUDENT", "ROLE_ADMIN") : AuthorityUtils.createAuthorityList("ROLE_STUDENT");
        return mvc.perform(request.with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, authorities))));
    }

    private int idOf(ResultActions result, String path) throws Exception {
        Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return value instanceof net.minidev.json.JSONArray array ? (Integer) array.getFirst() : (Integer) value;
    }

    private int account(String name) {
        return TestData.account(jdbc, name + "." + prefix);
    }

    private int insert(String sql) {
        return TestData.insert(jdbc, sql);
    }
}
