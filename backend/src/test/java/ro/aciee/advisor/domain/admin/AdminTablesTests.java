package ro.aciee.advisor.domain.admin;

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

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminTablesTests {

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    int adminId;
    String prefix;

    @BeforeEach
    void seed() {
        prefix = "a" + RUN.incrementAndGet();
        adminId = TestData.account(jdbc, "admin." + prefix);
        jdbc.sql("INSERT INTO core.user_role (user_id, role_code) VALUES (:id, 'admin')").param("id", adminId).update();
    }

    @Test
    void adminEditsTablesAndDatabaseErrorsBecomeCodes() throws Exception {
        String facultyId = created(write(post("/api/admin/tables/faculty"), "{\"code\":\"%s\",\"name_ro\":\"Facultate\"}".formatted(prefix)));
        write(post("/api/admin/tables/faculty"), "{\"code\":\"%s\",\"name_ro\":\"Alta\"}".formatted(prefix))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("duplicate"));
        String departmentId = created(write(post("/api/admin/tables/department"),
                "{\"faculty_id\":\"%s\",\"name_ro\":\"Calculatoare\"}".formatted(facultyId)));
        String professorId = created(write(post("/api/admin/tables/professor"),
                "{\"full_name\":\"Ion Popescu\",\"title_code\":\"conf\",\"department_id\":\"%s\",\"slug\":\"ion-popescu-%s\"}"
                        .formatted(departmentId, prefix)));

        as(get("/api/admin/tables/professor").param("search", "popescu"))
                .andExpect(jsonPath("$[0].department_id_label").value("Calculatoare"))
                .andExpect(jsonPath("$[0].title_code_label").value("Conf. univ. dr."));
        as(delete("/api/admin/tables/department/" + departmentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("foreign_key"));
        write(post("/api/admin/tables/academic_year"), "{\"during\":\"not a range\"}")
                .andExpect(jsonPath("$.detail").value("invalid_value"));
        as(get("/api/admin/audit").param("table", "professor"))
                .andExpect(jsonPath("$[0].actor").value("admin.%s@student.ugal.ro".formatted(prefix)));
        as(post("/api/admin/tables/professor/%s/archive".formatted(professorId))).andExpect(status().isNoContent());
        assertThat(as(get("/api/admin/audit").param("table", "professor").param("rowId", professorId))
                .andReturn().getResponse().getContentAsString())
                .contains("archived_at")
                .doesNotContain("Popescu");
        mvc.perform(get("/api/admin/tables").with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        adminId, null, AuthorityUtils.createAuthorityList("ROLE_STUDENT")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void groupGetsOneOfferingPerCurriculumItemOfItsYear() throws Exception {
        TestData.currentGroup(jdbc, prefix + "x", 30);
        int groupId = jdbc.sql("""
                        INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
                        SELECT p.id, (SELECT id FROM core.academic_year WHERE during @> current_date), 2, :code, 30
                        FROM core.study_plan p JOIN core.specialization s ON s.id = p.specialization_id
                        WHERE s.code = 'C' AND lower(p.intake) = 2025
                        RETURNING id""")
                .param("code", prefix)
                .query(Integer.class)
                .single();
        long expected = jdbc.sql("""
                        SELECT count(*) FROM core.curriculum_item ci JOIN core.study_group g ON g.plan_id = ci.plan_id
                        WHERE g.id = :group AND ci.study_year = 2""")
                .param("group", groupId)
                .query(Long.class).single();

        as(post("/api/admin/tables/study_group/%d/offerings".formatted(groupId))).andExpect(jsonPath("$.created").value(expected));
        as(post("/api/admin/tables/study_group/%d/offerings".formatted(groupId))).andExpect(jsonPath("$.created").value(0));
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String json) throws Exception {
        return as(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        var admin = UsernamePasswordAuthenticationToken.authenticated(adminId, null,
                AuthorityUtils.createAuthorityList("ROLE_STUDENT", "ROLE_ADMIN"));
        return mvc.perform(request.with(csrf()).with(authentication(admin)));
    }

    private String created(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
