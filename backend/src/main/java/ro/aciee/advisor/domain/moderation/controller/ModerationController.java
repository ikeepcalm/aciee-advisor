package ro.aciee.advisor.domain.moderation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.moderation.model.DecisionBody;
import ro.aciee.advisor.domain.moderation.model.Queue;
import ro.aciee.advisor.domain.moderation.model.ReportedPost;
import ro.aciee.advisor.domain.moderation.service.ModerationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @GetMapping("/queue")
    public Queue queue() {
        return moderationService.queue();
    }

    @PostMapping("/posts/{postId}/versions/{versionNo}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID postId, @PathVariable int versionNo) {
        moderationService.decide(postId, versionNo, null);
    }

    @PostMapping("/posts/{postId}/versions/{versionNo}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID postId, @PathVariable int versionNo, @Valid @RequestBody DecisionBody body) {
        moderationService.decide(postId, versionNo, body.getReasonCode());
    }

    @GetMapping("/reports")
    public List<ReportedPost> reports() {
        return moderationService.reports();
    }

    @PostMapping("/reports/{postId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable UUID postId) {
        moderationService.closeReports(postId, null);
    }

    @PostMapping("/reports/{postId}/uphold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uphold(@PathVariable UUID postId, @Valid @RequestBody DecisionBody body) {
        moderationService.closeReports(postId, body.getReasonCode());
    }
}
