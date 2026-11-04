package ro.aciee.advisor.domain.token;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import ro.aciee.advisor.TestData;
import ro.aciee.advisor.TestcontainersConfiguration;
import ro.aciee.advisor.domain.token.service.BlindRsa;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenIssueTests {

    private static final AtomicInteger RUN = new AtomicInteger();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Value("${app.token-keys-dir}")
    Path keysDir;

    int adminId;
    int studentId;
    int outsiderId;
    int semesterId;
    List<Integer> offerings;

    @BeforeEach
    void seed() {
        String prefix = "t" + RUN.incrementAndGet();
        int groupId = TestData.currentGroup(jdbc, prefix, 30);
        adminId = TestData.account(jdbc, "admin." + prefix);
        studentId = TestData.account(jdbc, "student." + prefix);
        outsiderId = TestData.account(jdbc, "outsider." + prefix);
        jdbc.sql("INSERT INTO core.user_role (user_id, role_code) VALUES (:id, 'admin')").param("id", adminId).update();
        transactions.executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.user_id', :admin, true)").param("admin", String.valueOf(adminId)).query(String.class).single();
            jdbc.sql("""
                            INSERT INTO core.group_membership (user_id, group_id, kind, source, valid_during)
                            VALUES (:user, :group, 'regular', 'university_sync', daterange(current_date - 30, current_date + 300))""")
                    .param("user", studentId).param("group", groupId).update();
        });
        semesterId = jdbc.sql("""
                        INSERT INTO core.semester (academic_year_id, no, during, rating_opens_at)
                        SELECT academic_year_id, 1, daterange(current_date - 30, current_date + 100), now() - interval '1 day'
                        FROM core.study_group WHERE id = :group
                        ON CONFLICT (academic_year_id, no) DO UPDATE SET rating_opens_at = excluded.rating_opens_at
                        RETURNING id""")
                .param("group", groupId).query(Integer.class).single();
        offerings = List.of(offering(prefix + "a", groupId), offering(prefix + "b", groupId));
    }

    @Test
    void studentGetsOneBlindSignaturePerEligibleOfferingAndRetriesGetTheSameOnes() throws Exception {
        send(post("/api/admin/semesters/%d/token-keys".formatted(semesterId)), studentId).andExpect(status().isForbidden());
        int created = Integer.parseInt(body(send(post("/api/admin/semesters/%d/token-keys".formatted(semesterId)), adminId)
                .andExpect(status().isOk())));
        assertThat(created).isGreaterThanOrEqualTo(2);
        send(post("/api/admin/semesters/%d/token-keys".formatted(semesterId)), adminId).andExpect(jsonPath("$").value(0));

        send(get("/api/tokens/offerings"), studentId)
                .andExpect(jsonPath("$[*].offeringId").value(contains(offerings.get(0), offerings.get(1))))
                .andExpect(jsonPath("$[*].open").value(contains(true, true)))
                .andExpect(jsonPath("$[*].issued").value(contains(false, false)));
        send(get("/api/tokens/offerings"), outsiderId).andExpect(jsonPath("$.length()").value(0));

        var first = Blinded.of(keys(offerings.get(0)));
        var second = Blinded.of(keys(offerings.get(1)));
        issue(studentId, item(offerings.get(0), first)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("incomplete_set"));

        String issued = body(issue(studentId, item(offerings.get(0), first), item(offerings.get(1), second)).andExpect(status().isOk()));
        assertThat(first.finalizeAndVerify(signature(issued, 0))).isTrue();
        assertThat(second.finalizeAndVerify(signature(issued, 1))).isTrue();

        assertThat(body(issue(studentId, item(offerings.get(0), first), item(offerings.get(1), second)))).isEqualTo(issued);
        send(get("/api/tokens/offerings"), studentId).andExpect(jsonPath("$[*].issued").value(contains(true, true)));
        issue(studentId, item(offerings.get(0), Blinded.of(keys(offerings.get(0))))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("already_issued"));
        issue(outsiderId, item(offerings.get(0), first)).andExpect(status().isForbidden());
    }

    @Test
    void vaultIsReadAndReplacedByItsOwnerOnly() throws Exception {
        send(get("/api/vault"), studentId).andExpect(status().isNotFound());
        saveVault(studentId, "AQID", "AAAAAAAAAAAAAAAA").andExpect(status().isNoContent());
        saveVault(studentId, "BAUG", "AAAAAAAAAAAAAAAA").andExpect(status().isNoContent());
        saveVault(studentId, "BAUG", "AAAA").andExpect(status().isBadRequest());
        send(get("/api/vault"), studentId).andExpect(jsonPath("$.ciphertext").value("BAUG"));
        send(get("/api/vault"), outsiderId).andExpect(status().isNotFound());
    }

    private ResultActions saveVault(int userId, String ciphertext, String nonce) throws Exception {
        return send(put("/api/vault").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ciphertext\":\"%s\",\"nonce\":\"%s\"}".formatted(ciphertext, nonce)), userId);
    }

    private int offering(String code, int groupId) {
        int subjectId = TestData.insert(jdbc, "INSERT INTO core.subject (code, name_ro) VALUES ('%s', 'Materie %s') RETURNING id".formatted(code, code));
        int itemId = TestData.insert(jdbc, """
                INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no)
                SELECT plan_id, %d, 3, 1 FROM core.study_group WHERE id = %d RETURNING id""".formatted(subjectId, groupId));
        return TestData.insert(jdbc, """
                INSERT INTO core.course_offering (curriculum_item_id, group_id, plan_id, study_year, title_snapshot)
                SELECT %d, id, plan_id, study_year, 'Materie' FROM core.study_group WHERE id = %d RETURNING id""".formatted(itemId, groupId));
    }

    private KeyPair keys(int offeringId) throws Exception {
        byte[] spki = jdbc.sql("SELECT public_key FROM anon.offering_key WHERE offering_id = :id AND active")
                .param("id", offeringId).query(byte[].class).single();
        byte[] fingerprint = jdbc.sql("SELECT fingerprint FROM anon.offering_key WHERE offering_id = :id AND active")
                .param("id", offeringId).query(byte[].class).single();
        var factory = KeyFactory.getInstance("RSA");
        var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(spki));
        var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(
                Files.readAllBytes(keysDir.resolve(HexFormat.of().formatHex(fingerprint) + ".der"))));
        return new KeyPair(publicKey, privateKey);
    }

    private ResultActions issue(int userId, String... items) throws Exception {
        return send(post("/api/tokens/issue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokens\":[" + String.join(",", items) + "]}"), userId);
    }

    private static String item(int offeringId, Blinded blinded) {
        return "{\"offeringId\":%d,\"blindedMsg\":\"%s\"}".formatted(offeringId, Base64.getEncoder().encodeToString(blinded.blindedMsg));
    }

    private static byte[] signature(String json, int index) {
        return Base64.getDecoder().decode((String) JsonPath.read(json, "$[%d].blindSig".formatted(index)));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, int userId) throws Exception {
        var authorities = userId == adminId ? AuthorityUtils.createAuthorityList("ROLE_STUDENT", "ROLE_ADMIN") : AuthorityUtils.createAuthorityList("ROLE_STUDENT");
        return mvc.perform(request.with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, authorities))));
    }

    static class Blinded {
        final RSAPublicKey key;
        final byte[] msg = new byte[32];
        final BigInteger r;
        final byte[] blindedMsg;

        Blinded(KeyPair keys) throws Exception {
            key = (RSAPublicKey) keys.getPublic();
            RANDOM.nextBytes(msg);
            BigInteger n = key.getModulus();
            r = new BigInteger(n.bitLength() - 1, RANDOM);
            // the client encodes EMSA-PSS itself; here the encoding is recovered from a plain signature
            BigInteger encoded = new BigInteger(1, plainSignature((RSAPrivateCrtKey) keys.getPrivate(), msg)).modPow(key.getPublicExponent(), n);
            blindedMsg = fixed(encoded.multiply(r.modPow(key.getPublicExponent(), n)).mod(n), n);
        }

        static Blinded of(KeyPair keys) throws Exception {
            return new Blinded(keys);
        }

        boolean finalizeAndVerify(byte[] blindSig) {
            BigInteger n = key.getModulus();
            byte[] sig = fixed(new BigInteger(1, blindSig).multiply(r.modInverse(n)).mod(n), n);
            return BlindRsa.verify(key, msg, sig);
        }

        static byte[] plainSignature(RSAPrivateCrtKey key, byte[] msg) throws Exception {
            var signer = Signature.getInstance("RSASSA-PSS");
            signer.setParameter(new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 0, 1));
            signer.initSign(key);
            signer.update(msg);
            return signer.sign();
        }

        static byte[] fixed(BigInteger value, BigInteger n) {
            int length = (n.bitLength() + 7) / 8;
            byte[] raw = value.toByteArray();
            byte[] out = new byte[length];
            int copy = Math.min(raw.length, length);
            System.arraycopy(raw, raw.length - copy, out, length - copy, copy);
            return out;
        }
    }
}
