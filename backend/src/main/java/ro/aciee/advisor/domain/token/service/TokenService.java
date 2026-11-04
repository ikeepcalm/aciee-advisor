package ro.aciee.advisor.domain.token.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.aciee.advisor.domain.token.model.IssuedToken;
import ro.aciee.advisor.domain.token.model.OfferingKey;
import ro.aciee.advisor.domain.token.model.RateableOffering;
import ro.aciee.advisor.domain.token.model.TokenRequest;
import ro.aciee.advisor.domain.token.model.Vault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String OFFERINGS = """
            SELECT o.offering_id, o.title_snapshot AS title, g.academic_year, o.semester_no,
                   o.rating_opens_at AS opens_at, coalesce(o.rating_opens_at <= now(), false) AS open, k.fingerprint,
                   EXISTS (SELECT 1 FROM ident.token_issuance t WHERE t.user_id = core.app_user_id() AND t.offering_id = o.offering_id) AS issued
            FROM core.offering_v o
            JOIN core.group_v g ON g.id = o.group_id
            LEFT JOIN anon.offering_key k ON k.offering_id = o.offering_id AND k.active
            WHERE EXISTS (SELECT 1 FROM core.group_membership m WHERE m.user_id = core.app_user_id() AND m.group_id = o.group_id)
               OR EXISTS (SELECT 1 FROM core.offering_enrolment e WHERE e.user_id = core.app_user_id() AND e.offering_id = o.offering_id)
            ORDER BY o.rating_opens_at NULLS LAST, o.semester_no, o.title_snapshot, o.offering_id""";

    private final JdbcClient jdbc;
    private final Map<String, RSAPrivateCrtKey> privateKeys = new ConcurrentHashMap<>();

    @Value("${app.token-keys-dir}")
    private Path keysDir;

    @Transactional(readOnly = true)
    public List<RateableOffering> offerings() {
        return jdbc.sql(OFFERINGS).query(RateableOffering.class).list();
    }

    @Transactional
    public List<IssuedToken> issue(List<TokenRequest> tokens) {
        Map<Integer, byte[]> requested = tokens.stream().collect(Collectors.toMap(TokenRequest::getOfferingId, TokenRequest::getBlindedMsg,
                (a, b) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate_offering");
                }));
        var pending = offerings().stream().filter(RateableOffering::pending).toList();
        if (!pending.stream().allMatch(p -> requested.containsKey(p.getOfferingId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "incomplete_set");
        }
        if (requested.isEmpty()) {
            return List.of();
        }
        pending.forEach(p -> store(p, requested.get(p.getOfferingId())));
        var issued = jdbc.sql("""
                        SELECT offering_id, blinded_msg, blind_sig FROM ident.token_issuance
                        WHERE user_id = core.app_user_id() AND offering_id IN (:offerings)
                        ORDER BY offering_id""")
                .param("offerings", requested.keySet())
                .query(IssuedToken.class)
                .list();
        if (issued.size() != requested.size()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not_eligible");
        }
        if (!issued.stream().allMatch(t -> Arrays.equals(t.getBlindedMsg(), requested.get(t.getOfferingId())))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_issued");
        }
        return issued;
    }

    public int generateKeys(int semesterId) throws GeneralSecurityException, IOException {
        var offerings = jdbc.sql("""
                        SELECT o.offering_id FROM core.offering_v o
                        WHERE o.semester_id = :semester
                          AND NOT EXISTS (SELECT 1 FROM anon.offering_key k WHERE k.offering_id = o.offering_id AND k.active)""")
                .param("semester", semesterId)
                .query(Integer.class)
                .list();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        Files.createDirectories(keysDir);
        for (int offeringId : offerings) {
            var pair = generator.generateKeyPair();
            byte[] publicKey = pair.getPublic().getEncoded();
            byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(publicKey);
            writePrivateKey(keyFile(HexFormat.of().formatHex(fingerprint)), pair.getPrivate().getEncoded());
            jdbc.sql("INSERT INTO anon.offering_key (fingerprint, offering_id, public_key) VALUES (:fingerprint, :offering, :key)")
                    .param("fingerprint", fingerprint)
                    .param("offering", offeringId)
                    .param("key", publicKey)
                    .update();
        }
        return offerings.size();
    }

    @Transactional(readOnly = true)
    public List<OfferingKey> keys() {
        return jdbc.sql("SELECT offering_id, fingerprint, public_key FROM anon.offering_key WHERE active ORDER BY offering_id")
                .query(OfferingKey.class)
                .list();
    }

    @Transactional(readOnly = true)
    public Vault vault() {
        return jdbc.sql("SELECT ciphertext, nonce FROM ident.token_vault WHERE user_id = core.app_user_id()")
                .query(Vault.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "vault_not_found"));
    }

    @Transactional
    public void saveVault(Vault vault) {
        jdbc.sql("""
                        INSERT INTO ident.token_vault (user_id, ciphertext, nonce) VALUES (core.app_user_id(), :ciphertext, :nonce)
                        ON CONFLICT (user_id) DO UPDATE SET ciphertext = excluded.ciphertext, nonce = excluded.nonce, updated_at = now()""")
                .param("ciphertext", vault.getCiphertext())
                .param("nonce", vault.getNonce())
                .update();
    }

    private void store(RateableOffering token, byte[] blindedMsg) {
        byte[] blindSig;
        try {
            blindSig = BlindRsa.blindSign(privateKey(token.getFingerprint()), blindedMsg);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_blinded_msg");
        }
        jdbc.sql("""
                        INSERT INTO ident.token_issuance (user_id, offering_id, issued_week, blinded_msg, blind_sig)
                        VALUES (core.app_user_id(), :offering, date_trunc('week', current_date)::date, :blinded, :sig)
                        ON CONFLICT DO NOTHING""")
                .param("offering", token.getOfferingId())
                .param("blinded", blindedMsg)
                .param("sig", blindSig)
                .update();
    }

    private RSAPrivateCrtKey privateKey(byte[] fingerprint) {
        return privateKeys.computeIfAbsent(HexFormat.of().formatHex(fingerprint), hex -> {
            try {
                var spec = new PKCS8EncodedKeySpec(Files.readAllBytes(keyFile(hex)));
                return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private Path keyFile(String hex) {
        return keysDir.resolve(hex + ".der");
    }

    private void writePrivateKey(Path file, byte[] encoded) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(file);
        }
        Files.write(file, encoded);
    }
}
