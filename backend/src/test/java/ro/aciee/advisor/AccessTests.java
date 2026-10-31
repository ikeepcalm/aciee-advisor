package ro.aciee.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class AccessTests {

    @Autowired
    JdbcClient jdbc;

    int groupId;
    int adminId;
    int leaderId;
    int studentId;
    int outsiderId;

    @BeforeEach
    void seed() {
        groupId = TestData.currentGroup(jdbc, "3311", 30);
        adminId = account("admin");
        leaderId = account("leader");
        studentId = account("student");
        outsiderId = account("outsider");
        jdbc.sql("INSERT INTO core.user_role (user_id, role_code) VALUES (:id, 'admin')").param("id", adminId).update();

        actAs(adminId);
        int leaderMembership = member(leaderId);
        int claimId = insert("""
                INSERT INTO core.group_leader_claim (user_id, group_id, status, decided_by, decided_at)
                VALUES (%d, %d, 'approved', %d, now()) RETURNING id""".formatted(leaderId, groupId, adminId));
        jdbc.sql("INSERT INTO core.group_leader (membership_id, claim_id) VALUES (:m, :c)")
                .param("m", leaderMembership).param("c", claimId).update();
        actAs(leaderId);
        member(studentId);
    }

    @Test
    void studentsSeeOnlyTheirOwnMembership() {
        actAs(studentId);
        assertThat(visibleMembers()).isEqualTo(1);
        actAs(outsiderId);
        assertThat(visibleMembers()).isZero();
        actAs(leaderId);
        assertThat(visibleMembers()).isEqualTo(2);
    }

    @Test
    void studentCannotApproveThemselves() {
        actAs(outsiderId);
        assertThatThrownBy(() -> member(outsiderId))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void identPoolCannotReadAnonymousData() {
        assertThatThrownBy(() -> jdbc.sql("SELECT count(*) FROM anon.submission").query(Long.class).single())
                .rootCause().hasMessageContaining("permission denied");
    }

    private long visibleMembers() {
        return jdbc.sql("SELECT count(*) FROM core.group_membership").query(Long.class).single();
    }

    private int member(int userId) {
        return insert("""
                INSERT INTO core.group_membership (user_id, group_id, kind, source, valid_during)
                VALUES (%d, %d, 'regular', 'university_sync', '[2025-10-01,2027-10-01)') RETURNING id""".formatted(userId, groupId));
    }

    private int account(String name) {
        return TestData.account(jdbc, name);
    }

    private void actAs(int userId) {
        jdbc.sql("SELECT set_config('app.user_id', :id, true)").param("id", String.valueOf(userId)).query().listOfRows();
    }

    private int insert(String sql) {
        return TestData.insert(jdbc, sql);
    }
}
