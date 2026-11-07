package ro.aciee.advisor.domain.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import ro.aciee.advisor.domain.auth.model.Account;

import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SecurityContextRepository securityContextRepository;

    public void start(Account account, HttpServletRequest request, HttpServletResponse response) {
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
