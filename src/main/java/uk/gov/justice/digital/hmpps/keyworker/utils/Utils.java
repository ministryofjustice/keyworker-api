package uk.gov.justice.digital.hmpps.keyworker.utils;

import com.google.common.io.BaseEncoding;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

public class Utils {

    /**
     * Base64-decode a string. Works for both url-safe and non-url-safe
     * encodings.
     *
     * @param base64Encoded
     * @return
     */
    private static byte[] base64Decode(String base64Encoded) {
        if (base64Encoded.contains("+") || base64Encoded.contains("/")) {
            return BaseEncoding.base64().decode(base64Encoded);
        } else {
            return BaseEncoding.base64Url().decode(base64Encoded);
        }
    }

    /**
     * Load the private key from a URL-safe base64 encoded string
     *
     * @param encodedPrivateKey
     * @return
     * @throws NoSuchProviderException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    public static PrivateKey loadPrivateKey(String encodedPrivateKey) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedPrivateKey = base64Decode(encodedPrivateKey);

        // prime256v1 is NIST P-256
        ECParameterSpec params = ECNamedCurveTable.getParameterSpec("prime256v1");
        ECPrivateKeySpec prvkey = new ECPrivateKeySpec(new BigInteger(decodedPrivateKey), params);
        KeyFactory kf = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);

        return kf.generatePrivate(prvkey);
    }
}