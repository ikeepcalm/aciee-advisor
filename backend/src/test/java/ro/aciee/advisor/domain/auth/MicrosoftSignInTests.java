package ro.aciee.advisor.domain.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import ro.aciee.advisor.TestData;
import ro.aciee.advisor.TestcontainersConfiguration;
import ro.aciee.advisor.domain.auth.controller.MicrosoftSignInHandler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MicrosoftSignInTests {

    private static final String UGAL = "445e5699-60fa-4f59-8e7f-778197a4d740";
    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired
    MicrosoftSignInHandler handler;

    @Autowired
    JdbcClient jdbc;

    @Test
    void newStudentGetsAPasswordlessAccountAndASession() throws Exception {
        var oid = UUID.randomUUID();
        var email = "ms" + RUN.incrementAndGet() + "@student.ugal.ro";

        var session = signIn(UGAL, oid, email);

        assertThat(session.getRedirect()).isEqualTo("/signin");
        int userId = (Integer) session.principal();
        assertThat(jdbc.sql("SELECT entra_oid = :oid AND password_hash IS NULL FROM core.user_account WHERE id = :id")
                .param("oid", oid).param("id", userId).query(Boolean.class).single()).isTrue();
        assertThat(signIn(UGAL, oid, email).principal()).isEqualTo(userId);
    }

    @Test
    void otherTenantsAndDomainsAreRejectedAndTheirSessionDropped() throws Exception {
        var otherTenant = signIn(UUID.randomUUID().toString(), UUID.randomUUID(), "ms" + RUN.incrementAndGet() + "@student.ugal.ro");
        assertThat(otherTenant.getRedirect()).isEqualTo("/signin?error=microsoft_tenant");
        assertThat(otherTenant.getSession().isInvalid()).isTrue();

        var staff = signIn(UGAL, UUID.randomUUID(), "teacher@ugal.ro");
        assertThat(staff.getRedirect()).isEqualTo("/signin?error=microsoft_domain");
        assertThat(staff.getSession().isInvalid()).isTrue();
    }

    @Test
    void linkingAnUnverifiedAccountDropsItsPlantedPassword() throws Exception {
        var email = "ms" + RUN.incrementAndGet();
        int userId = TestData.account(jdbc, email);
        jdbc.sql("INSERT INTO core.email_verification (user_id, code_hash) VALUES (:id, sha256('x'::bytea))")
                .param("id", userId).update();

        assertThat(signIn(UGAL, UUID.randomUUID(), email + "@student.ugal.ro").principal()).isEqualTo(userId);
        assertThat(jdbc.sql("""
                        SELECT password_hash IS NULL AND NOT EXISTS (SELECT 1 FROM core.email_verification WHERE user_id = :id)
                        FROM core.user_account WHERE id = :id""")
                .param("id", userId).query(Boolean.class).single()).isTrue();
    }

    @Test
    void linkingAVerifiedAccountKeepsItsPasswordAndRefusesASecondMicrosoftAccount() throws Exception {
        var email = "ms" + RUN.incrementAndGet();
        int userId = TestData.account(jdbc, email);

        assertThat(signIn(UGAL, UUID.randomUUID(), email + "@student.ugal.ro").principal()).isEqualTo(userId);
        assertThat(jdbc.sql("SELECT password_hash FROM core.user_account WHERE id = :id").param("id", userId)
                .query(String.class).single()).isEqualTo("x");
        assertThat(signIn(UGAL, UUID.randomUUID(), email + "@student.ugal.ro").getRedirect())
                .isEqualTo("/signin?error=microsoft_conflict");
    }

    @Test
    void microsoftFillsTheNameOnceAndKeepsACorrectedOne() throws Exception {
        var oid = UUID.randomUUID();
        var email = "ms" + RUN.incrementAndGet() + "@student.ugal.ro";
        int userId = (Integer) signIn(UGAL, oid, email, "  HOROKH   Bohdan ").principal();
        assertThat(name(userId)).isEqualTo("HOROKH Bohdan");

        jdbc.sql("UPDATE core.user_account SET display_name = 'Bohdan Horokh' WHERE id = :id").param("id", userId).update();
        signIn(UGAL, oid, email, "HOROKH Bohdan");
        assertThat(name(userId)).isEqualTo("Bohdan Horokh");
    }

    @Test
    void anOverlongMicrosoftNameIsSkippedWithoutFailingTheSignIn() throws Exception {
        var email = "ms" + RUN.incrementAndGet() + "@student.ugal.ro";
        var result = signIn(UGAL, UUID.randomUUID(), email, "x".repeat(81));
        assertThat(result.getRedirect()).isEqualTo("/signin");
        assertThat(name((Integer) result.principal())).isNull();
    }

    private String name(int userId) {
        return jdbc.sql("SELECT display_name FROM core.user_account WHERE id = :id").param("id", userId)
                .query(String.class).optional().orElse(null);
    }

    @Test
    void disabledAccountsCannotSignIn() throws Exception {
        var email = "ms" + RUN.incrementAndGet();
        int userId = TestData.account(jdbc, email);
        jdbc.sql("UPDATE core.user_account SET disabled_at = now() WHERE id = :id").param("id", userId).update();

        assertThat(signIn(UGAL, UUID.randomUUID(), email + "@student.ugal.ro").getRedirect()).isEqualTo("/signin?error=disabled");
    }

    private Result signIn(String tenant, UUID oid, String email) throws Exception {
        return signIn(tenant, oid, email, null);
    }

    private Result signIn(String tenant, UUID oid, String email, String name) throws Exception {
        var token = OidcIdToken.withTokenValue("token")
                .subject(oid.toString())
                .claim("tid", tenant)
                .claim("oid", oid.toString())
                .claim("preferred_username", email)
                .claims(claims -> {
                    if (name != null) {
                        claims.put("name", name);
                    }
                })
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var authentication = new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "microsoft");
        var session = new MockHttpSession();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication);
        return new Result(response.getRedirectedUrl(), session);
    }

    @Getter
    @AllArgsConstructor
    private static class Result {
        private final String redirect;
        private final MockHttpSession session;

        Object principal() {
            var context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            return context.getAuthentication().getPrincipal();
        }
    }
}
