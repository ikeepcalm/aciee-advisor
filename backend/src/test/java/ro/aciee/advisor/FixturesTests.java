package ro.aciee.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import ro.aciee.advisor.domain.catalog.model.Radar;
import ro.aciee.advisor.domain.catalog.service.CatalogService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.liquibase.contexts=fixtures", "spring.datasource.username=test", "spring.datasource.password=test"})
@Import(TestcontainersConfiguration.class)
class FixturesTests {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CatalogService catalog;

    @Test
    void smallFixturesFillEveryTableAtAboutTwoPercent() {
        assertThat(count("core.user_account")).isBetween(1_000L, 1_500L);
        assertThat(count("core.group_membership")).isBetween(3_000L, 4_500L);
        assertThat(count("core.membership_request WHERE status = 'pending'")).isPositive();
        assertThat(count("core.group_leader")).isPositive();
        assertThat(count("core.teaching_assignment")).isGreaterThan(count("core.course_offering"));
        assertThat(count("anon.submission")).isBetween(20_000L, 40_000L);
        assertThat(count("anon.rating")).isGreaterThan(count("anon.submission"));
        assertThat(count("anon.post_version")).isGreaterThan(count("anon.post"));
        assertThat(count("anon.published_post")).isPositive();
        assertThat(count("core.audit_log")).isBetween(100_000L, 110_000L);
        assertThat(count("anon.prof_subject_radar WHERE a01 IS NOT NULL")).isPositive();
        assertThat(count("core.user_role r JOIN core.user_account a ON a.id = r.user_id WHERE a.email LIKE 'demo.%'")).isEqualTo(2);
    }

    @Test
    void pagesShowOneRadarPerClassTypeAndExamShares() {
        int projectId = jdbc.sql("""
                        INSERT INTO core.teaching_assignment (offering_id, group_id, professor_id, component, valid_during)
                        SELECT ta.offering_id, ta.group_id, ta.professor_id, 'project', ta.valid_during
                        FROM core.teaching_assignment ta
                        WHERE EXISTS (SELECT 1 FROM anon.rating r WHERE r.teaching_assignment_id = ta.id)
                          AND NOT EXISTS (SELECT 1 FROM core.teaching_assignment o WHERE o.offering_id = ta.offering_id AND o.component = 'project')
                        LIMIT 1
                        RETURNING id""")
                .query(Integer.class).single();
        jdbc.sql("""
                        INSERT INTO anon.rating (key_hash, teaching_assignment_id, offering_id, a01)
                        SELECT s.key_hash, ta.id, s.offering_id, 7
                        FROM core.teaching_assignment ta JOIN anon.submission s ON s.offering_id = ta.offering_id
                        WHERE ta.id = :id""")
                .param("id", projectId).update();
        jdbc.sql("SELECT anon.refresh_aggregates()").query().listOfRows();
        Map<String, Object> pair = jdbc.sql("""
                        SELECT p.slug, r.subject_id, (SELECT ci.id FROM core.curriculum_item ci
                            JOIN core.subject_root_v sr ON sr.subject_id = ci.subject_id WHERE sr.root_id = r.subject_id LIMIT 1) AS item_id
                        FROM anon.prof_subject_radar r JOIN core.professor p ON p.id = r.professor_id
                        GROUP BY p.slug, r.subject_id HAVING count(*) > 1 LIMIT 1""")
                .query().singleRow();
        var professor = catalog.professor((String) pair.get("slug"));
        var subject = professor.getSubjects().stream().filter(s -> s.getSubjectId() == (Integer) pair.get("subject_id")).findFirst().orElseThrow();
        assertThat(subject.getRadars()).hasSizeGreaterThan(1).extracting(Radar::getComponent).doesNotHaveDuplicates();
        assertThat(professor.getRadar().getScores()).hasSize(24);

        var course = catalog.course((Integer) pair.get("item_id"), null);
        assertThat(course.getTeachers()).allSatisfy(teacher -> {
            if (teacher.getRadar() != null) {
                assertThat(teacher.getRadar().getComponent()).isEqualTo(teacher.getComponent());
            }
        });
        assertThat(catalog.lookups().getExamOptions()).hasSize(7);
        assertThat(catalog.professors()).isNotEmpty();
        assertThat(count("anon.course_survival WHERE position('%' IN exam_form) > 0 AND exam_content IS NOT NULL")).isPositive();
    }

    private long count(String from) {
        return jdbc.sql("SELECT count(*) FROM " + from).query(Long.class).single();
    }
}
