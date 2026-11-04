package ro.aciee.advisor.domain.token;

import org.junit.jupiter.api.Test;
import ro.aciee.advisor.domain.token.service.BlindRsa;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlindRsaTests {

    static final String P = "e1f4d7a34802e27c7392a3cea32a262a34dc3691bd87f3f310dc75673488930559c120fd0410194fb8a0da55bd0b81227e843fdca6692ae80e5a5d414116d4803fca7d8c30eaaae57e44a1816ebb5c5b0606c536246c7f11985d731684150b63c9a3ad9e41b04c0b5b27cb188a692c84696b742a80d3cd00ab891f2457443dadfeba6d6daf108602be26d7071803c67105a5426838e6889d77e8474b29244cefaf418e381b312048b457d73419213063c60ee7b0d81820165864fef93523c9635c22210956e53a8d96322493ffc58d845368e2416e078e5bcb5d2fd68ae6acfa54f9627c42e84a9d3f2774017e32ebca06308a12ecc290c7cd1156dcccfb2311";
    static final String Q = "c601a9caea66dc3835827b539db9df6f6f5ae77244692780cd334a006ab353c806426b60718c05245650821d39445d3ab591ed10a7339f15d83fe13f6a3dfb20b9452c6a9b42eaa62a68c970df3cadb2139f804ad8223d56108dfde30ba7d367e9b0a7a80c4fdba2fd9dde6661fc73fc2947569d2029f2870fc02d8325acf28c9afa19ecf962daa7916e21afad09eb62fe9f1cf91b77dc879b7974b490d3ebd2e95426057f35d0a3c9f45f79ac727ab81a519a8b9285932d9b2e5ccd347e59f3f32ad9ca359115e7da008ab7406707bd0e8e185a5ed8758b5ba266e8828f8d863ae133846304a2936ad7bc7c9803879d2fc4a28e69291d73dbd799f8bc238385";
    static final String E = "010001";
    static final String D = "0d43242aefe1fb2c13fbc66e20b678c4336d20b1808c558b6e62ad16a287077180b177e1f01b12f9c6cd6c52630257ccef26a45135a990928773f3bd2fc01a313f1dac97a51cec71cb1fd7efc7adffdeb05f1fb04812c924ed7f4a8269925dad88bd7dcfbc4ef01020ebfc60cb3e04c54f981fdbd273e69a8a58b8ceb7c2d83fbcbd6f784d052201b88a9848186f2a45c0d2826870733e6fd9aa46983e0a6e82e35ca20a439c5ee7b502a9062e1066493bdadf8b49eb30d9558ed85abc7afb29b3c9bc644199654a4676681af4babcea4e6f71fe4565c9c1b85d9985b84ec1abf1a820a9bbebee0df1398aae2c85ab580a9f13e7743afd3108eb32100b870648fa6bc17e8abac4d3c99246b1f0ea9f7f93a5dd5458c56d9f3f81ff2216b3c3680a13591673c43194d8e6fc93fc1e37ce2986bd628ac48088bc723d8fbe293861ca7a9f4a73e9fa63b1b6d0074f5dea2a624c5249ff3ad811b6255b299d6bc5451ba7477f19c5a0db690c3e6476398b1483d10314afd38bbaf6e2fbdbcd62c3ca9797a420ca6034ec0a83360a3ee2adf4b9d4ba29731d131b099a38d6a23cc463db754603211260e99d19affc902c915d7854554aabf608e3ac52c19b8aa26ae042249b17b2d29669b5c859103ee53ef9bdc73ba3c6b537d5c34b6d8f034671d7f3a8a6966cc4543df223565343154140fd7391c7e7be03e241f4ecfeb877a051";
    static final String PREPARED_MSG = "8f3dc6fb8c4a02f4d6352edf0907822c1210a9b32f9bdda4c45a698c80023aa6b59f8cfec5fdbb36331372ebefedae7d";
    static final String BLINDED_MSG = "982790826556aabe6004467671a864397eea3b95740e9a11c8b80b99ee0cf4dbc50af860bda81b601a2eceaa6943ef104f13325ad0be2e37f42030b3120e87cfee8cfe59cde1acfb25485a43275ebe777292e2518181ae531e596f988ff16f458daa5a42408939cbe60e7271391a21657276427d195bee6a20054101d4ceb892ecdea402ea1a866acf0e451a3336f07e7589330d96c3883fd5bc1a829a715b618b74a86b2a898764246ad081d4c9f1edb8ab5077e315fde2417ec2dd33cad93e120340b49be89c18a63e62c6bb289037283d3bf18608be11ee4c823c710b0c6b89235fed3f03a7b96ddd25a8f54f20dac37ce8905093ad8e066810f354fb1773236e3d3788ba755de2c9bce8d340078bb1831ddc7314a5018673427ced65cb356281aae08b5e6636f3eb2417e09d6ae476a9abcc410bc8c90813d0740e39ae75efae4c02eed49dbb7aa51258bb71197445d17a6029bf566ba6b36282173af2c42e9b9631366f22eb6a19ef1d92bd3ce0631d3a7fb3288195b0ba380a3828d5411cefd5eba83e52198c001ac9946a333a33d89d4d235fc833239d59837f04eaf065e9563659b00c7624a6263b727d8f2c07959ba2bb592e7ff251b8f09c85995fd2e4474e743586576b518230986b6076b762ae77088a37e4bffd2ef41ae68d6d4e79205290b4f76c42ef039638c41cdc6fe8af9b429c0dee45b2942e3861da2a";
    static final String BLIND_SIG = "362ef369f9b8c1487e285514702a7cd6fe03e4a2fb854881f3d3f986b7742a0c9bfab6562a6cd5ed71c574af67d7e77e71b33420c08ebb0ff37886b858297f9562fc366066c6d8e77bad1918b04756ba03f5c385d44f06759daf1b7a38b2a64248dee95d0e3886c8afa1f74afd8ac3c56520d0f3fd206df8e0d257312756803b09a79d0cc38112592c3aec32de5a9bc3284c5a0a2d0808b102deafa5cc60f04e3d71c0284cba04f17f88aa8e07d5544fe0265807d515877f79d30ed26d522b9d9c56597647b0dbca5a69d6418f8d1b51481723f272c2a3d48f6f4fd6beeac3576c3edb00e8779964548aeab8e004c7c4f8ef9cb6e680e2d2d49792004bb3e6974fa48f241a361ca449c02bd4c0ad4e66252c55e656f16049908efe59acbafa1171895dfac64d909808e5420469d622c7253ec1de7522b41634d383bf8786bf881cbf1561627f1e62b2d93300ec30ec0f5f0ab32036fce068bc76b0b0c6452079537f8d7f8dcee4b42bbf2d9ad7499d3835cd93cfc7e8ebea3554ab5241e181e5d73241b7bebf0a281b63594a35f4993e2b416d60db966b58b648cfcba2c4bee4c2830aae4a70ff55012480298f549c13b1b2684277bca12f592471b8a99285174f1c0ebb38fc80e74a10b3f02ec3e6682ba873f7ff0e1e79718b470927c74ed754d4f7c3d9a55e22246e829cdb5a1c6fb2a0a6c896df303063c918bcf5eb0017";
    static final String SIG = "4454b6983ff01cb28545329f394936efa42ed231e15efbc025fdaca00277acf0c8e00e3d8b0ecebd35b057b8ebfc14e1a7097368a4abd20b555894ccef3d1b9528c6bcbda6b95376bef230d0f1feff0c1064c62c60a7ae7431d1fdfa43a81eed9235e363e1ffa0b2797aba6aad6082fcd285e14fc8b71de6b9c87cb4059c7dc1e96ae1e63795a1e9af86b9073d1d848aef3eca8a03421bcd116572456b53bcfd4dabb0a9691f1fabda3ed0ce357aee2cfee5b1a0eb226f69716d4e011d96eede5e38a9acb531a64336a0d5b0bae3ab085b658692579a376740ff6ce69e89b06f360520b864e33d82d029c808248a19e18e31f0ecd16fac5cd4870f8d3ebc1c32c718124152dc905672ab0b7af48bf7d1ac1ff7b9c742549c91275ab105458ae37621757add83482bbcf779e777bbd61126e93686635d4766aedf5103cf7978f3856ccac9e28d21a850dbb03c811128616d315d717be1c2b6254f8509acae862042c034530329ce15ca2e2f6b1f5fd59272746e3918c748c0eb810bf76884fa10fcf749326bbfaa5ba285a0186a22e4f628dbf178d3bb5dc7e165ca73f6a55ecc14c4f5a26c4693ce5da032264cbec319b12ddb9787d0efa4fcf1e5ccee35ad85ecd453182df9ed735893f830b570faae8be0f6fe2e571a4e0d927cba4debd368d3b4fca33ec6251897a137cf75474a32ac8256df5e5ffa518b88b43fb6f63a24";

    static final HexFormat HEX = HexFormat.of();

    static BigInteger num(String hex) {
        return new BigInteger(hex, 16);
    }

    RSAPrivateCrtKey privateKey() throws Exception {
        BigInteger p = num(P), q = num(Q), d = num(D);
        var spec = new RSAPrivateCrtKeySpec(p.multiply(q), num(E), d, p, q,
                d.mod(p.subtract(BigInteger.ONE)), d.mod(q.subtract(BigInteger.ONE)), q.modInverse(p));
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    RSAPublicKey publicKey() throws Exception {
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(num(P).multiply(num(Q)), num(E)));
    }

    @Test
    void blindSignMatchesRfc9474Vector() throws Exception {
        assertThat(HEX.formatHex(BlindRsa.blindSign(privateKey(), HEX.parseHex(BLINDED_MSG)))).isEqualTo(BLIND_SIG);
    }

    @Test
    void verifiesRfc9474Signature() throws Exception {
        assertThat(BlindRsa.verify(publicKey(), HEX.parseHex(PREPARED_MSG), HEX.parseHex(SIG))).isTrue();
    }

    @Test
    void rejectsTamperedMessageOrSignature() throws Exception {
        byte[] msg = HEX.parseHex(PREPARED_MSG);
        msg[0] ^= 1;
        byte[] sig = HEX.parseHex(SIG);
        sig[sig.length - 1] ^= 1;
        assertThat(BlindRsa.verify(publicKey(), msg, HEX.parseHex(SIG))).isFalse();
        assertThat(BlindRsa.verify(publicKey(), HEX.parseHex(PREPARED_MSG), sig)).isFalse();
        assertThat(BlindRsa.verify(publicKey(), HEX.parseHex(PREPARED_MSG), new byte[3])).isFalse();
    }

    @Test
    void rejectsBlindedMessageOutOfRange() throws Exception {
        byte[] aboveModulus = new byte[512];
        Arrays.fill(aboveModulus, (byte) 0xff);
        assertThatThrownBy(() -> BlindRsa.blindSign(privateKey(), aboveModulus)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlindRsa.blindSign(privateKey(), new byte[10])).isInstanceOf(IllegalArgumentException.class);
    }
}
