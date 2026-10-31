package ro.aciee.advisor.domain.leadership.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.leadership.model.Leadership;
import ro.aciee.advisor.domain.leadership.service.LeadershipService;

@RestController
@RequestMapping("/api/admin/leaders")
@RequiredArgsConstructor
public class LeadershipController {

    private final LeadershipService leadershipService;

    @GetMapping
    public Leadership overview() {
        return leadershipService.overview();
    }

    @PostMapping("/claims/{claimId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable int claimId) {
        leadershipService.approve(claimId);
    }

    @PostMapping("/claims/{claimId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable int claimId) {
        leadershipService.reject(claimId);
    }

    @DeleteMapping("/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable int membershipId) {
        leadershipService.revoke(membershipId);
    }
}
