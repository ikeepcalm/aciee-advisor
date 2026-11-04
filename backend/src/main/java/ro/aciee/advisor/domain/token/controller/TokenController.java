package ro.aciee.advisor.domain.token.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.token.model.IssueBody;
import ro.aciee.advisor.domain.token.model.IssuedToken;
import ro.aciee.advisor.domain.token.model.OfferingKey;
import ro.aciee.advisor.domain.token.model.PendingToken;
import ro.aciee.advisor.domain.token.service.TokenService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @GetMapping("/api/tokens/pending")
    public List<PendingToken> pending() {
        return tokenService.pending();
    }

    @PostMapping("/api/tokens/issue")
    public List<IssuedToken> issue(@Valid @RequestBody IssueBody body) {
        return tokenService.issue(body.getTokens());
    }

    @PostMapping("/api/admin/semesters/{semesterId}/token-keys")
    public int generateKeys(@PathVariable int semesterId) throws GeneralSecurityException, IOException {
        return tokenService.generateKeys(semesterId);
    }

    @GetMapping("/anon/keys.json")
    public List<OfferingKey> keys() {
        return tokenService.keys();
    }
}
