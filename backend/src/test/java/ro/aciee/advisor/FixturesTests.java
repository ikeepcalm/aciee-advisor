package ro.aciee.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.liquibase.contexts=fixtures", "spring.datasource.username=test", "spring.datasource.password=test"})
@Import(TestcontainersConfiguration.class)
class FixturesTests {

    @Autowired
    JdbcClient jdbc;

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

    private long count(String from) {
        return jdbc.sql("SELECT count(*) FROM " + from).query(Long.class).single();
    }
}
