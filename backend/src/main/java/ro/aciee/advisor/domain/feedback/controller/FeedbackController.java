package ro.aciee.advisor.domain.feedback.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.auth.service.AccountService;
import ro.aciee.advisor.domain.feedback.model.FeedbackRequest;
import ro.aciee.advisor.domain.feedback.service.FeedbackService;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AccountService accountService;

    @PostMapping("/api/feedback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@Valid @RequestBody FeedbackRequest body, Authentication authentication) {
        String accountEmail = authentication != null && authentication.getPrincipal() instanceof Integer userId
                ? accountService.findById(userId).getEmail()
                : null;
        feedbackService.send(body.getMessage(), body.getPage(), body.getReplyTo(), accountEmail);
    }
}
