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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        int claimId = idOf(send(get("/api/admin/leaders"), adminId).andExpect(status().isOk()), "$.claims[0].id");
        send(post("/api/admin/leaders/claims/%d/approve".formatted(claimId)), adminId).andExpect(status().isNoContent());
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

        send(get("/api/groups/mine"), studentId)
                .andExpect(jsonPath("$.memberships[0].subgroup").value("1"))
                .andExpect(jsonPath("$.memberships[0].leader").value(false));
        send(get("/api/groups/%d/members".formatted(groupId)), leaderId).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void fullGroupAndUsedUpInviteAreRefused() throws Exception {
        int leaderId = account("leader");
        send(post("/api/groups/%d/leader-claims".formatted(groupId)), leaderId);
        int claimId = idOf(send(get("/api/admin/leaders"), adminId), "$.claims[?(@.groupCode == '%s')].id".formatted(prefix));
        send(post("/api/admin/leaders/claims/%d/approve".formatted(claimId)), adminId).andExpect(status().isNoContent());
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
