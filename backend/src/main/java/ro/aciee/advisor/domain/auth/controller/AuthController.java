package ro.aciee.advisor.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.auth.model.Account;
import ro.aciee.advisor.domain.auth.model.ResendCodeRequest;
import ro.aciee.advisor.domain.auth.model.SignInRequest;
import ro.aciee.advisor.domain.auth.model.SignUpRequest;
import ro.aciee.advisor.domain.auth.model.VerifyRequest;
import ro.aciee.advisor.domain.auth.service.AccountService;

import java.util.stream.Stream;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void signUp(@Valid @RequestBody SignUpRequest body) {
        accountService.signUp(body.getEmail(), body.getPassword());
    }

    @PostMapping("/verify")
    public Account verify(@Valid @RequestBody VerifyRequest body, HttpServletRequest request, HttpServletResponse response) {
        var account = accountService.verify(body.getEmail(), body.getCode(), body.getPassword());
        signIn(account, request, response);
        return account;
    }

    @PostMapping("/verify/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendCode(@Valid @RequestBody ResendCodeRequest body) {
        accountService.resendCode(body.getEmail());
    }

    @PostMapping("/signin")
    public Account signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest request, HttpServletResponse response) {
        var account = accountService.authenticate(body.getEmail(), body.getPassword());
        signIn(account, request, response);
        return account;
    }

    @GetMapping("/me")
    public Account me(Authentication authentication) {
        return accountService.findById((Integer) authentication.getPrincipal());
    }

    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken();
    }

    private void signIn(Account account, HttpServletRequest request, HttpServletResponse response) {
        var authorities = Stream.concat(Stream.of("student"), account.getRoles().stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .toList();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account.getId(), null, authorities));
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        securityContextRepository.saveContext(context, request, response);
    }
}
