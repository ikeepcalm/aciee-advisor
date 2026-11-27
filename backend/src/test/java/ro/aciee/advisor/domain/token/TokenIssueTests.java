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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    int moderatorId;
    int semesterId;
    List<Integer> offerings;
    List<Integer> items = new ArrayList<>();
    String prefix;

    @BeforeEach
    void seed() {
        prefix = "t" + RUN.incrementAndGet();
        items.clear();
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

    @Test
    void tokenRedeemsOnceThenIsReadEditedModeratedAndDeleted() throws Exception {
        moderatorId = TestData.account(jdbc, "moderator." + prefix);
        jdbc.sql("INSERT INTO core.user_role (user_id, role_code) VALUES (:id, 'moderator')").param("id", moderatorId).update();
        int departmentId = TestData.insert(jdbc, """
                INSERT INTO core.department (faculty_id, name_ro) SELECT id, 'Calculatoare' FROM core.faculty WHERE code = '%s' RETURNING id"""
                .formatted(prefix));
        int professorId = TestData.insert(jdbc, """
                INSERT INTO core.professor (full_name, title_code, department_id, slug) VALUES ('Ion Popescu', 'conf', %d, 'ion-popescu-%s') RETURNING id"""
                .formatted(departmentId, prefix));
        int offeringId = offerings.get(0);
        int assignmentId = TestData.insert(jdbc, """
                INSERT INTO core.teaching_assignment (offering_id, group_id, professor_id, component, valid_during)
                SELECT id, group_id, %d, 'lecture', daterange(current_date - 30, NULL) FROM core.course_offering WHERE id = %d RETURNING id"""
                .formatted(professorId, offeringId));
        send(post("/api/admin/semesters/%d/token-keys".formatted(semesterId)), adminId).andExpect(status().isOk());
        var token = Blinded.of(keys(offeringId));
        var other = Blinded.of(keys(offerings.get(1)));
        String issued = body(issue(studentId, item(offeringId, token), item(offerings.get(1), other)).andExpect(status().isOk()));
        byte[] sig = token.unblind(signature(issued, 0));
        byte[] fingerprint = jdbc.sql("SELECT fingerprint FROM anon.offering_key WHERE offering_id = :id AND active")
                .param("id", offeringId).query(byte[].class).single();
        String msg = Base64.getEncoder().encodeToString(token.msg);
        String form = """
                {"offeringId":%d,"fingerprint":"%s","msg":"%s","sig":"%%s","examDifficulty":6,"exam":["practical","theory"],
                 "ratings":[{"teachingAssignmentId":%d,"scores":[7,null,null,null,9,null,null,null,null,null,null,null,null,5,null,null,null,null,null,null,null,null,null,null],"comment":""}],
                 "tip":"%%s"}""".formatted(offeringId, Base64.getEncoder().encodeToString(fingerprint), msg, assignmentId);
        String goodSig = Base64.getEncoder().encodeToString(sig);
        byte[] badSig = sig.clone();
        badSig[0] ^= 1;

        anon(post("/anon/submissions").content(form.formatted(Base64.getEncoder().encodeToString(badSig), "x")))
                .andExpect(jsonPath("$.detail").value("invalid_token"));
        anon(post("/anon/submissions").content(form.formatted(goodSig, "Lucrați la laborator."))).andExpect(status().isCreated());
        anon(post("/anon/submissions").content(form.formatted(goodSig, "Din nou"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("already_submitted"));
        anon(get("/anon/offerings/" + offeringId)).andExpect(jsonPath("$.assignments[0].professor").value("Ion Popescu"));
        anon(get("/anon/submissions/" + offeringId).header("X-Token", msg))
                .andExpect(jsonPath("$.exam[0]").value("practical"))
                .andExpect(jsonPath("$.ratings[0].scores[4]").value(9))
                .andExpect(jsonPath("$.ratings[0].scores[13]").value(nullValue()))
                .andExpect(jsonPath("$.posts[0].status").value("pending"));

        String postId = jdbc.sql("SELECT id::text FROM anon.post WHERE offering_id = :id AND kind = 'tip'")
                .param("id", offeringId).query(String.class).single();
        send(get("/api/moderation/queue"), moderatorId).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(greaterThan(0)));
        send(get("/api/moderation/queue"), studentId).andExpect(status().isForbidden());
        send(post("/api/moderation/posts/%s/versions/1/approve".formatted(postId)), moderatorId).andExpect(status().isNoContent());
        send(post("/api/moderation/posts/%s/versions/1/approve".formatted(postId)), moderatorId).andExpect(status().isConflict());

        jdbc.sql("SELECT anon.refresh_aggregates()").query().listOfRows();
        int itemId = items.get(0);
        mvc.perform(get("/api/catalog/courses/" + itemId)).andExpect(jsonPath("$.teachers[0].fullName").value("Ion Popescu"))
                .andExpect(jsonPath("$.tips[0].body").value("Lucrați la laborator."));
        send(get("/api/catalog/courses/" + itemId), studentId)
                .andExpect(jsonPath("$.tips[0].body").value("Lucrați la laborator."))
                .andExpect(jsonPath("$.teachers[0].radar.n").value(1))
                .andExpect(jsonPath("$.survival.n").value(1));
        send(get("/api/professors/ion-popescu-" + prefix), studentId).andExpect(jsonPath("$.subjects[0].itemId").value(itemId));

        anon(put("/anon/submissions/" + offeringId).header("X-Token", msg).content(form.formatted(goodSig, "Lucrați mult la laborator.")))
                .andExpect(status().isNoContent());
        anon(get("/anon/submissions/" + offeringId).header("X-Token", msg))
                .andExpect(jsonPath("$.posts[0].status").value("pending"))
                .andExpect(jsonPath("$.posts[0].published").value(true));

        anon(post("/anon/posts/%s/reports".formatted(postId)).content("{\"reasonCode\":\"personal_data\"}")).andExpect(status().isNoContent());
        send(get("/api/moderation/reports"), moderatorId).andExpect(jsonPath("$[?(@.postId == '%s')].count".formatted(postId)).value(contains(1)));
        send(post("/api/moderation/reports/%s/uphold".formatted(postId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"personal_data\"}"), moderatorId).andExpect(status().isNoContent());
        send(get("/api/catalog/courses/" + itemId), studentId).andExpect(jsonPath("$.tips.length()").value(0));

        anon(delete("/anon/submissions/" + offeringId).header("X-Token", msg)).andExpect(status().isNoContent());
        anon(get("/anon/submissions/" + offeringId).header("X-Token", msg)).andExpect(status().isNotFound());
    }

    private ResultActions anon(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
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
        items.add(itemId);
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
        var authorities = AuthorityUtils.createAuthorityList("ROLE_STUDENT", userId == adminId ? "ROLE_ADMIN" : userId == moderatorId ? "ROLE_MODERATOR" : "ROLE_STUDENT");
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

        byte[] unblind(byte[] blindSig) {
            BigInteger n = key.getModulus();
            return fixed(new BigInteger(1, blindSig).multiply(r.modInverse(n)).mod(n), n);
        }

        boolean finalizeAndVerify(byte[] blindSig) {
            return BlindRsa.verify(key, msg, unblind(blindSig));
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
