package ro.aciee.advisor.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.jdbc.config.annotation.SpringSessionTransactionManager;
import ro.aciee.advisor.domain.auth.controller.MicrosoftSignInHandler;

import javax.sql.DataSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
                                            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                            MicrosoftSignInHandler microsoftSignInHandler) {
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(login -> login
                    .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2"))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/code/*"))
                    .successHandler(microsoftSignInHandler)
                    .failureHandler(microsoftSignInHandler));
        }
        return http
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/anon/**"))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/signup", "/api/auth/signin", "/api/auth/verify", "/api/auth/verify/resend", "/api/auth/csrf",
                                "/api/auth/providers", "/api/auth/oauth2/**").permitAll()
                        .requestMatchers("/api/catalog/**").permitAll()
                        .requestMatchers("/api/admin/**", "/api/reports/**").hasRole("ADMIN")
                        .requestMatchers("/api/moderation/**").hasAnyRole("MODERATOR", "ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/signout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @SpringSessionTransactionManager
    JdbcTransactionManager sessionTransactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
