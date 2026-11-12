package ro.aciee.advisor.domain.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ro.aciee.advisor.TestcontainersConfiguration;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTests {

    private static final Pattern CODE = Pattern.compile("\\b\\d{6}\\b");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    JavaMailSender mailSender;

    @Test
    void signUpVerifyAndSignIn() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ana@student.ugal.ro", "long-enough-pw")))
                .andExpect(status().isForbidden());

        send("/api/auth/signup", credentials("Ana+x@student.ugal.ro", "long-enough-pw")).andExpect(status().isAccepted());
        var code = sentCode();
        send("/api/auth/signin", credentials("ana@student.ugal.ro", "long-enough-pw"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("unverified"));
        send("/api/auth/verify", verification("ana@student.ugal.ro", otherThan(code), "long-enough-pw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.attemptsLeft").value(4));

        var session = send("/api/auth/verify", verification("ana@student.ugal.ro", code, "long-enough-pw"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("Ana+x@student.ugal.ro"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        send("/api/auth/signin", credentials("ANA@student.ugal.ro", "wrong-password")).andExpect(status().isUnauthorized());
        send("/api/auth/signin", credentials("ANA@student.ugal.ro", "long-enough-pw")).andExpect(status().isOk());
        send("/api/auth/signup", credentials("ana@student.ugal.ro", "another-password")).andExpect(status().isConflict());
    }

    @Test
    void repeatedSignUpIsIdempotent() throws Exception {
        send("/api/auth/signup", credentials("dan@student.ugal.ro", "first-password")).andExpect(status().isAccepted());
        var code = sentCode();
        send("/api/auth/signup", credentials("dan@student.ugal.ro", "second-password")).andExpect(status().isAccepted());
        verifyNoInteractions(mailSender);
        send("/api/auth/verify", verification("dan@student.ugal.ro", code, "second-password")).andExpect(status().isOk());
    }

    @Test
    void reSigningUpCannotPlantAPasswordOnSomeoneElsesAccount() throws Exception {
        send("/api/auth/signup", credentials("victim@student.ugal.ro", "victim-password")).andExpect(status().isAccepted());
        expireCooldown();
        send("/api/auth/signup", credentials("victim+x@student.ugal.ro", "attacker-password")).andExpect(status().isAccepted());
        var code = sentCode();

        send("/api/auth/verify", verification("victim@student.ugal.ro", code, "victim-password"))
                .andExpect(status().isUnauthorized());
        send("/api/auth/signup", credentials("victim@student.ugal.ro", "victim-password")).andExpect(status().isAccepted());
        send("/api/auth/verify", verification("victim@student.ugal.ro", code, "victim-password")).andExpect(status().isOk());
        send("/api/auth/signin", credentials("victim@student.ugal.ro", "attacker-password")).andExpect(status().isUnauthorized());
    }

    @Test
    void codeLocksAfterFiveAttempts() throws Exception {
        send("/api/auth/signup", credentials("bogdan@student.ugal.ro", "long-enough-pw")).andExpect(status().isAccepted());
        var code = sentCode();
        for (int i = 0; i < 5; i++) {
            send("/api/auth/verify", verification("bogdan@student.ugal.ro", otherThan(code), "long-enough-pw"))
                    .andExpect(status().isBadRequest());
        }
        send("/api/auth/verify", verification("bogdan@student.ugal.ro", code, "long-enough-pw")).andExpect(status().isGone());
    }

    @Test
    void resendIsThrottledAndSilentForUnknownEmails() throws Exception {
        send("/api/auth/verify/resend", "{\"email\":\"nobody@student.ugal.ro\"}").andExpect(status().isAccepted());
        verifyNoInteractions(mailSender);
        send("/api/auth/signup", credentials("carmen@student.ugal.ro", "long-enough-pw")).andExpect(status().isAccepted());
        send("/api/auth/verify/resend", "{\"email\":\"carmen@student.ugal.ro\"}").andExpect(status().isTooManyRequests());
        expireCooldown();
        send("/api/auth/verify/resend", "{\"email\":\"carmen@student.ugal.ro\"}").andExpect(status().isAccepted());
        send("/api/auth/verify", verification("carmen@student.ugal.ro", sentCode(), "long-enough-pw")).andExpect(status().isOk());
    }

    @Test
    void rejectsAddressesOutsideTheStudentDomain() throws Exception {
        send("/api/auth/signup", credentials("ana@gmail.com", "long-enough-pw")).andExpect(status().isBadRequest());
        send("/api/auth/signup", credentials("\\\"a b\\\"@student.ugal.ro", "long-enough-pw")).andExpect(status().isBadRequest());
    }

    private void expireCooldown() {
        jdbc.sql("UPDATE core.email_verification SET sent_at = now() - interval '2 minutes'").update();
    }

    private ResultActions send(String path, String body) throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("ACIEE-XSRF");
        assertThat(token).isNotNull();
        clearInvocations(mailSender);
        return mvc.perform(post(path)
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String sentCode() {
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        var matcher = CODE.matcher(message.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private static String otherThan(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String verification(String email, String code, String password) {
        return "{\"email\":\"%s\",\"code\":\"%s\",\"password\":\"%s\"}".formatted(email, code, password);
    }
}
