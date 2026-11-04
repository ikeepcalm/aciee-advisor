package ro.aciee.advisor.domain.token.service;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public class BlindRsa {

    private static final PSSParameterSpec PSS_ZERO = new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 0, 1);

    public static byte[] blindSign(RSAPrivateCrtKey key, byte[] blindedMsg) {
        BigInteger n = key.getModulus();
        int modulusLength = (n.bitLength() + 7) / 8;
        BigInteger m = new BigInteger(1, blindedMsg);
        if (blindedMsg.length != modulusLength || m.compareTo(n) >= 0) {
            throw new IllegalArgumentException("Blinded message out of range");
        }
        BigInteger s = m.modPow(key.getPrivateExponent(), n);
        if (!s.modPow(key.getPublicExponent(), n).equals(m)) {
            throw new IllegalStateException("Signing failed");
        }
        return toFixedLength(s, modulusLength);
    }

    public static boolean verify(RSAPublicKey key, byte[] preparedMsg, byte[] sig) {
        try {
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(PSS_ZERO);
            verifier.initVerify(key);
            verifier.update(preparedMsg);
            return verifier.verify(sig);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] toFixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[length];
        int copy = Math.min(raw.length, length);
        System.arraycopy(raw, raw.length - copy, out, length - copy, copy);
        return out;
    }
}
