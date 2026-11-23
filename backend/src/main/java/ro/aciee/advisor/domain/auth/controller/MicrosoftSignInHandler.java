package ro.aciee.advisor.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.auth.service.AccountService;
import ro.aciee.advisor.domain.auth.service.SessionService;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MicrosoftSignInHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final AccountService accountService;
    private final SessionService sessionService;

    @Value("${app.entra-tenant-id}")
    private String tenantId;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        var user = (OidcUser) authentication.getPrincipal();
        try {
            if (!tenantId.equals(user.getClaimAsString("tid"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "microsoft_tenant");
            }
            var email = user.getEmail() != null ? user.getEmail() : user.getPreferredUsername();
            var account = accountService.signInWithMicrosoft(UUID.fromString(user.getClaimAsString("oid")), email, user.getFullName());
            sessionService.start(account, request, response);
            response.sendRedirect("/signin");
        } catch (ResponseStatusException e) {
            signOut(request);
            response.sendRedirect("/signin?error=" + e.getReason());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        signOut(request);
        response.sendRedirect("/signin?error=microsoft_failed");
    }

    private void signOut(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
