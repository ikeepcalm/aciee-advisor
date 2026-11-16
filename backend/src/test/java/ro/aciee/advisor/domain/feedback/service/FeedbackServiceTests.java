package ro.aciee.advisor.domain.feedback.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FeedbackServiceTests {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-16T12:00:00Z"));

    private FeedbackService service(String to) {
        var clock = new Clock() {
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            public Instant instant() {
                return now.get();
            }
        };
        var service = new FeedbackService(mailSender, clock);
        ReflectionTestUtils.setField(service, "mailFrom", "ACIEE Advisor <no-reply@acieeadvisor.com>");
        ReflectionTestUtils.setField(service, "feedbackTo", to);
        ReflectionTestUtils.setField(service, "publicUrl", "https://acieeadvisor.com");
        return service;
    }

    @Test
    void mailsTheMessageWithThePageAndRepliesToTheSignedInStudent() {
        service("admin@example.com").send("  Lipsește orarul pentru 22C32A\nrestul e ok", "/timetable?g=22C32A",
                "other@example.com", "bh110@student.ugal.ro");

        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("admin@example.com");
        assertThat(sent.getValue().getReplyTo()).isEqualTo("bh110@student.ugal.ro");
        assertThat(sent.getValue().getSubject()).isEqualTo("[ACIEE Advisor] Lipsește orarul pentru 22C32A");
        assertThat(sent.getValue().getText()).contains("Pagina: https://acieeadvisor.com/timetable?g=22C32A");
    }

    @Test
    void anonymousMessagesUseTheGivenReplyAddressOrNone() {
        service("admin@example.com").send("Bug", null, " ", null);

        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getReplyTo()).isNull();
        assertThat(sent.getValue().getText()).contains("Cont: neautentificat", "Pagina: https://acieeadvisor.com/");
    }

    @Test
    void capsMessagesPerHourAndRefusesWhenNoInboxIsConfigured() {
        var service = service("admin@example.com");
        for (int i = 0; i < FeedbackService.HOURLY_LIMIT; i++) {
            service.send("m" + i, "/", null, null);
        }
        assertThatThrownBy(() -> service.send("one more", "/", null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("feedback_busy");

        now.set(now.get().plusSeconds(3601));
        service.send("next hour", "/", null, null);
        verify(mailSender, times(FeedbackService.HOURLY_LIMIT + 1)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service("").send("x", "/", null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("feedback_unavailable");
    }
}
