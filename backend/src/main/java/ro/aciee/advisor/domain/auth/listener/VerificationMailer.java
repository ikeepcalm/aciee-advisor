package ro.aciee.advisor.domain.auth.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ro.aciee.advisor.domain.auth.model.VerificationCodeIssued;

@Component
@RequiredArgsConstructor
public class VerificationMailer {

    private final JavaMailSender mailSender;

    @Value("${app.mail-from}")
    private String mailFrom;

    @TransactionalEventListener(fallbackExecution = true)
    public void send(VerificationCodeIssued event) {
        var message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(event.getEmail());
        message.setSubject("Cod de verificare ACIEE Advisor");
        message.setText("Codul tău de verificare este " + event.getCode() + ".\nExpiră în 15 minute.\n");
        mailSender.send(message);
    }
}
