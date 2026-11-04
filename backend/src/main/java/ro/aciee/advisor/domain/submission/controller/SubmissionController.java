package ro.aciee.advisor.domain.submission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.submission.model.OfferingForm;
import ro.aciee.advisor.domain.submission.model.ReportBody;
import ro.aciee.advisor.domain.submission.model.Submission;
import ro.aciee.advisor.domain.submission.model.SubmissionBody;
import ro.aciee.advisor.domain.submission.service.SubmissionService;

import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/anon")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @GetMapping("/offerings/{offeringId}")
    public OfferingForm form(@PathVariable int offeringId) {
        return submissionService.form(offeringId);
    }

    @PostMapping("/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody SubmissionBody body) {
        submissionService.create(body);
    }

    @GetMapping("/submissions/{offeringId}")
    public Submission find(@PathVariable int offeringId, @RequestHeader("X-Token") String token) {
        return submissionService.find(offeringId, Base64.getDecoder().decode(token));
    }

    @PutMapping("/submissions/{offeringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable int offeringId, @RequestHeader("X-Token") String token, @Valid @RequestBody SubmissionBody body) {
        submissionService.update(offeringId, Base64.getDecoder().decode(token), body);
    }

    @DeleteMapping("/submissions/{offeringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int offeringId, @RequestHeader("X-Token") String token) {
        submissionService.delete(offeringId, Base64.getDecoder().decode(token));
    }

    @PostMapping("/posts/{postId}/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable UUID postId, @Valid @RequestBody ReportBody body) {
        submissionService.report(postId, body.getReasonCode());
    }
}
