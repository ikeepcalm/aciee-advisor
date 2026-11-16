package ro.aciee.advisor.domain.feedback.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

@Service
public class FeedbackService {

    static final int HOURLY_LIMIT = 20;

    private final JavaMailSender mailSender;
    private final Clock clock;
    private final Deque<Instant> recent = new ArrayDeque<>();

    @Value("${app.mail-from}")
    private String mailFrom;

    @Value("${app.feedback-to:}")
    private String feedbackTo;

    @Value("${app.public-url}")
    private String publicUrl;

    @Autowired
    public FeedbackService(JavaMailSender mailSender) {
        this(mailSender, Clock.systemUTC());
    }

    FeedbackService(JavaMailSender mailSender, Clock clock) {
        this.mailSender = mailSender;
        this.clock = clock;
    }

    public void send(String message, String page, String replyTo, String accountEmail) {
        if (feedbackTo == null || feedbackTo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "feedback_unavailable");
        }
        admit();
        String text = message.strip();
        String answerTo = accountEmail != null ? accountEmail : blankToNull(replyTo);
        var mail = new SimpleMailMessage();
        mail.setFrom(mailFrom);
        mail.setTo(feedbackTo);
        if (answerTo != null) {
            mail.setReplyTo(answerTo);
        }
        mail.setSubject("[ACIEE Advisor] " + firstLine(text));
        mail.setText(text + "\n\n---\nPagina: " + publicUrl + (page == null || page.isBlank() ? "/" : page.strip())
                + "\nCont: " + (accountEmail != null ? accountEmail : "neautentificat")
                + "\nRăspuns la: " + (answerTo != null ? answerTo : "nu a lăsat o adresă") + "\n");
        mailSender.send(mail);
    }

    // shortcut: one site-wide cap, since behind the CDN the client IP is not trusted yet; add a per-IP limit once CF-Connecting-IP is.
    private synchronized void admit() {
        Instant now = clock.instant();
        while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
            recent.pollFirst();
        }
        if (recent.size() >= HOURLY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "feedback_busy");
        }
        recent.addLast(now);
    }

    private static String firstLine(String text) {
        String line = text.lines().findFirst().orElse("").strip();
        return line.length() > 70 ? line.substring(0, 70) + "…" : line;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
