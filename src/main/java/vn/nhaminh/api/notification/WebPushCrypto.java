package vn.nhaminh.api.notification;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mã hóa Web Push theo chuẩn, chỉ dùng thư viện có sẵn của Java:
 * - RFC 8291 + RFC 8188 (aes128gcm): mã hóa nội dung thông báo cho đúng thiết bị nhận.
 * - RFC 8292 (VAPID): ký JWT ES256 để máy chủ push (Apple/Google/Mozilla) biết thông báo đến từ server này.
 */
public final class WebPushCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

    private WebPushCrypto() {}

    // ================= Khóa P-256 =================

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không tạo được khóa P-256.", exception);
        }
    }

    /** Khóa công khai dạng điểm không nén 65 byte: 0x04 || X || Y. */
    public static byte[] encodePublicKey(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(unsigned32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(unsigned32(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    public static ECPublicKey decodePublicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new GeneralSecurityException("Khóa công khai P-256 không hợp lệ.");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
    }

    public static PrivateKey decodePrivateKey(byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    // ================= Mã hóa nội dung (RFC 8291) =================

    /**
     * @param uaPublicKey khóa "p256dh" của thiết bị (65 byte)
     * @param authSecret  "auth" của thiết bị (16 byte)
     * @return thân request gửi tới máy chủ push (header aes128gcm + bản mã)
     */
    public static byte[] encrypt(byte[] payload, byte[] uaPublicKey, byte[] authSecret) throws GeneralSecurityException {
        return encrypt(payload, uaPublicKey, authSecret, generateKeyPair(), randomBytes(16));
    }

    /** Bản có thể truyền khóa tạm & salt cố định — chỉ để kiểm thử. */
    static byte[] encrypt(byte[] payload, byte[] uaPublicKey, byte[] authSecret, KeyPair ephemeral, byte[] salt)
            throws GeneralSecurityException {
        if (payload.length > RECORD_SIZE - 17 - 86) {
            throw new GeneralSecurityException("Nội dung thông báo quá dài.");
        }
        byte[] asPublic = encodePublicKey((ECPublicKey) ephemeral.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(decodePublicKey(uaPublicKey), true);
        byte[] sharedSecret = agreement.generateSecret();

        // IKM = HKDF(salt=auth_secret, ikm=ecdh_secret, info="WebPush: info\0" || ua_public || as_public, 32)
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublicKey, asPublic);
        byte[] ikm = hkdf(authSecret, sharedSecret, keyInfo, 32);
        // CEK & NONCE từ HKDF(salt, IKM)
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // Một bản ghi duy nhất: nội dung + byte phân cách 0x02 (bản ghi cuối).
        byte[] padded = Arrays.copyOf(payload, payload.length + 1);
        padded[payload.length] = 0x02;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        // Header: salt(16) || rs(4) || idlen(1) || keyid(as_public 65)
        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    // ================= VAPID (RFC 8292) =================

    /** Giá trị header Authorization: "vapid t=<JWT>, k=<khóa công khai>". */
    public static String vapidAuthorization(String audience, String subject, PrivateKey privateKey, byte[] publicKey,
            long expiresAtEpochSeconds) throws GeneralSecurityException {
        String header = B64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64URL.encodeToString(("{\"aud\":\"" + jsonEscape(audience) + "\",\"exp\":" + expiresAtEpochSeconds
                + ",\"sub\":\"" + jsonEscape(subject) + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        // P1363 = chữ ký dạng R||S 64 byte như JWT yêu cầu (không phải DER).
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String jwt = signingInput + "." + B64URL.encodeToString(signer.sign());
        return "vapid t=" + jwt + ", k=" + B64URL.encodeToString(publicKey);
    }

    // ================= Tiện ích =================

    public static String base64Url(byte[] bytes) {
        return B64URL.encodeToString(bytes);
    }

    /** Chấp nhận cả base64url và base64 thường (có/không padding). */
    public static byte[] fromBase64Url(String value) {
        String normalized = value.trim().replace('+', '-').replace('/', '_').replace("=", "");
        return B64URL_DECODER.decode(normalized);
    }

    /** HKDF-SHA256 (RFC 5869), độ dài ≤ 32 byte nên chỉ cần một khối. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 0x01);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) return raw;
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32); // bỏ byte dấu 0x00
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length); // đệm 0 phía trước
        }
        return out;
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
