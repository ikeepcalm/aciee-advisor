package ro.aciee.advisor.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.auth.model.Account;
import ro.aciee.advisor.domain.auth.model.VerificationCodeIssued;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String SELECT_ACCOUNT = """
            SELECT a.id, a.email, a.password_hash, a.disabled_at IS NOT NULL AS disabled,
                   EXISTS (SELECT 1 FROM core.email_verification v WHERE v.user_id = a.id) AS unverified,
                   array(SELECT r.role_code FROM core.user_role r WHERE r.user_id = a.id ORDER BY r.role_code) AS roles
            FROM core.user_account a
            """;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void signUp(String email, String password) {
        int userId = jdbc.sql("""
                        INSERT INTO core.user_account (email, password_hash) VALUES (:email, :hash)
                        ON CONFLICT (email_normalized) DO UPDATE
                            SET email = EXCLUDED.email, password_hash = EXCLUDED.password_hash
                            WHERE EXISTS (SELECT 1 FROM core.email_verification v WHERE v.user_id = user_account.id)
                        RETURNING id""")
                .param("email", email)
                .param("hash", passwordEncoder.encode(password))
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "already_registered"));
        issueCode(userId, email);
    }

    @Transactional
    public void resendCode(String email) {
        var pending = findByEmail(email).filter(Account::isUnverified);
        if (pending.isPresent() && !issueCode(pending.get().getId(), pending.get().getEmail())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "code_recently_sent");
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Account verify(String email, String code, String password) {
        var attempt = jdbc.sql("""
                        UPDATE core.email_verification v SET attempts = v.attempts + 1
                        FROM core.user_account a
                        WHERE a.id = v.user_id AND a.email_normalized = core.normalize_email(:email)
                          AND v.attempts < 5 AND v.sent_at > now() - interval '15 minutes'
                        RETURNING v.user_id, a.password_hash,
                                  v.code_hash = sha256(convert_to(:code, 'UTF8')) AS matches,
                                  5 - v.attempts AS attempts_left""")
                .param("email", email)
                .param("code", code)
                .query()
                .listOfRows();
        if (attempt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.GONE, "code_expired");
        }
        var row = attempt.getFirst();
        if (!(Boolean) row.get("matches")) {
            var error = new ResponseStatusException(HttpStatus.BAD_REQUEST, "code_invalid");
            error.getBody().setProperty("attemptsLeft", row.get("attempts_left"));
            throw error;
        }
        if (!passwordMatches(password, (String) row.get("password_hash"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad_credentials");
        }
        int userId = (Integer) row.get("user_id");
        jdbc.sql("DELETE FROM core.email_verification WHERE user_id = :id").param("id", userId).update();
        var account = findById(userId);
        if (account.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "disabled");
        }
        return account;
    }

    public Account authenticate(String email, String password) {
        var account = findByEmail(email)
                .filter(found -> passwordMatches(password, found.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad_credentials"));
        if (account.isUnverified()) {
            issueCode(account.getId(), account.getEmail());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "unverified");
        }
        if (account.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "disabled");
        }
        return account;
    }

    @Transactional
    public Account signInWithMicrosoft(UUID oid, String email) {
        if (email == null || !email.toLowerCase(Locale.ROOT).endsWith("@student.ugal.ro")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "microsoft_domain");
        }
        int userId = jdbc.sql("SELECT id FROM core.user_account WHERE entra_oid = :oid")
                .param("oid", oid)
                .query(Integer.class)
                .optional()
                .orElseGet(() -> linkMicrosoftAccount(oid, email));
        jdbc.sql("DELETE FROM core.email_verification WHERE user_id = :id").param("id", userId).update();
        var account = findById(userId);
        if (account.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "disabled");
        }
        return account;
    }

    private int linkMicrosoftAccount(UUID oid, String email) {
        return jdbc.sql("""
                        INSERT INTO core.user_account (email, entra_oid) VALUES (:email, :oid)
                        ON CONFLICT (email_normalized) DO UPDATE
                            SET entra_oid = EXCLUDED.entra_oid,
                                password_hash = CASE
                                    WHEN EXISTS (SELECT 1 FROM core.email_verification v WHERE v.user_id = user_account.id) THEN NULL
                                    ELSE user_account.password_hash END
                            WHERE user_account.entra_oid IS NULL
                        RETURNING id""")
                .param("email", email)
                .param("oid", oid)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "microsoft_conflict"));
    }

    public Account findById(int id) {
        return jdbc.sql(SELECT_ACCOUNT + "WHERE a.id = :id")
                .param("id", id)
                .query(this::mapAccount)
                .single();
    }

    private Optional<Account> findByEmail(String email) {
        return jdbc.sql(SELECT_ACCOUNT + "WHERE a.email_normalized = core.normalize_email(:email)")
                .param("email", email)
                .query(this::mapAccount)
                .optional();
    }

    private boolean passwordMatches(String password, String hash) {
        return hash != null && passwordEncoder.matches(password, hash);
    }

    private boolean issueCode(int userId, String email) {
        var code = "%06d".formatted(random.nextInt(1_000_000));
        int issued = jdbc.sql("""
                        INSERT INTO core.email_verification (user_id, code_hash)
                        VALUES (:userId, sha256(convert_to(:code, 'UTF8')))
                        ON CONFLICT (user_id) DO UPDATE
                            SET code_hash = EXCLUDED.code_hash, sent_at = now(), attempts = 0
                            WHERE email_verification.sent_at < now() - interval '1 minute'""")
                .param("userId", userId)
                .param("code", code)
                .update();
        if (issued == 0) {
            return false;
        }
        events.publishEvent(new VerificationCodeIssued(email, code));
        return true;
    }

    private Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                rs.getInt("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getBoolean("disabled"),
                rs.getBoolean("unverified"),
                List.of((String[]) rs.getArray("roles").getArray()));
    }
}
