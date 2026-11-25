package vn.nhaminh.api.notification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.family.FamilyMembership;
import vn.nhaminh.api.family.FamilyMembershipRepository;
import vn.nhaminh.api.transaction.TransactionCreatedEvent;
import vn.nhaminh.api.transaction.TransactionType;
import vn.nhaminh.api.user.User;

/**
 * Ghi chú: bản mã do WebPushCrypto tạo đã được đối chiếu thủ công với thư viện http_ece (Node),
 * thư viện mà gói "web-push" dùng — giải mã khớp từng byte. Test dưới đây giải mã lại theo đúng
 * các bước của RFC 8291 phía trình duyệt để chặn sai sót khi sửa code sau này.
 */
class WebPushTest {

    @Test
    void encryptedPayloadDecryptsLikeABrowserWould() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] browserPublic = WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        byte[] message = "{\"title\":\"Mẹ vừa thêm khoản chi\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(message, browserPublic, auth);

        // Header aes128gcm: salt(16) | rs(4) | idlen(1) | keyid(65)
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertEquals(4096, buffer.getInt());
        int idLength = buffer.get();
        assertEquals(65, idLength);
        byte[] serverPublic = new byte[idLength];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPushCrypto.decodePublicKey(serverPublic), true);
        byte[] shared = agreement.generateSecret();
        byte[] info = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, serverPublic);
        byte[] ikm = WebPushCrypto.hkdf(auth, shared, info, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);

        assertEquals(0x02, padded[padded.length - 1]); // byte phân cách của bản ghi cuối
        assertArrayEquals(message, Arrays.copyOf(padded, padded.length - 1));
    }

    @Test
    void vapidHeaderHasSignedJwtAndPublicKey() {
        KeyPair vapid = WebPushCrypto.generateKeyPair();
        byte[] publicKey = WebPushCrypto.encodePublicKey((ECPublicKey) vapid.getPublic());

        String header = assertDoesNotThrow(() -> WebPushCrypto.vapidAuthorization(
                "https://web.push.apple.com", "mailto:a@b.c", vapid.getPrivate(), publicKey, 1_900_000_000L));

        assertTrue(header.startsWith("vapid t="));
        String jwt = header.substring("vapid t=".length(), header.indexOf(','));
        assertEquals(3, jwt.split("\\.").length);
        assertEquals(64, WebPushCrypto.fromBase64Url(jwt.split("\\.")[2]).length); // ES256 = R||S 64 byte
        assertTrue(header.endsWith(", k=" + WebPushCrypto.base64Url(publicKey)));
    }

    @Test
    void onlyOfficialPushServicesAreAllowed() {
        for (String ok : List.of(
                "https://fcm.googleapis.com/fcm/send/abc",
                "https://web.push.apple.com/QGb1abc",
                "https://updates.push.services.mozilla.com/wpush/v2/abc",
                "https://wns2-sg2p.notify.windows.com/w/?token=abc")) {
            assertDoesNotThrow(() -> PushService.validateEndpoint(ok), ok);
        }
        for (String bad : List.of(
                "http://fcm.googleapis.com/fcm/send/abc",
                "https://evil.example.com/push",
                "https://fcm.googleapis.com.evil.com/x",
                "https://fcm.googleapis.com:8443/x",
                "https://user@fcm.googleapis.com/x",
                "https://127.0.0.1/x",
                "https://localhost/x")) {
            assertThrows(ResponseStatusException.class, () -> PushService.validateEndpoint(bad), bad);
        }
    }

    @Test
    void notificationTextDescribesTheTransaction() {
        var message = TransactionNotifier.toMessage(new TransactionCreatedEvent(
                5L, 1L, 2L, "Mẹ", TransactionType.EXPENSE, new BigDecimal("250000"), "Ăn uống", "Con", "mua sữa", false));

        assertEquals("Mẹ vừa thêm khoản chi", message.title());
        assertEquals("-250.000 ₫ · Ăn uống · cho Con — mua sữa", message.body());
        assertEquals("tx-5", message.tag());

        var income = TransactionNotifier.toMessage(new TransactionCreatedEvent(
                6L, 1L, 2L, "Bố", TransactionType.INCOME, new BigDecimal("15000000"), "Lương", null, null, false));
        assertEquals("Bố vừa thêm khoản thu", income.title());
        assertEquals("+15.000.000 ₫ · Lương", income.body());
    }

    @Test
    void notifiesOtherFamilyMembersButNotTheOneWhoAddedIt() {
        Family family = new Family("Nhà mình");
        User mom = mock(User.class);
        User dad = mock(User.class);
        when(mom.getId()).thenReturn(1L);
        when(dad.getId()).thenReturn(2L);
        FamilyMembershipRepository memberships = mock(FamilyMembershipRepository.class);
        when(memberships.findMembers(10L)).thenReturn(List.of(
                new FamilyMembership(family, mom, "OWNER"), new FamilyMembership(family, dad, "MEMBER")));
        PushService push = mock(PushService.class);
        when(push.sendToUsers(any(), any())).thenReturn(new PushService.SendResult(1, 0));

        new TransactionNotifier(memberships, push).onTransactionCreated(new TransactionCreatedEvent(
                5L, 10L, 1L, "Mẹ", TransactionType.EXPENSE, new BigDecimal("250000"), "Ăn uống", null, "mua sữa", false));

        verify(push).sendToUsers(eq(List.of(2L)), any());
    }

    @Test
    void personAloneInFamilyGetsNoNotification() {
        User mom = mock(User.class);
        when(mom.getId()).thenReturn(1L);
        FamilyMembershipRepository memberships = mock(FamilyMembershipRepository.class);
        when(memberships.findMembers(10L)).thenReturn(List.of(new FamilyMembership(new Family("Nhà"), mom, "OWNER")));
        PushService push = mock(PushService.class);

        new TransactionNotifier(memberships, push).onTransactionCreated(new TransactionCreatedEvent(
                5L, 10L, 1L, "Mẹ", TransactionType.INCOME, BigDecimal.TEN, "Lương", null, null, false));

        verify(push, never()).sendToUsers(any(), any());
    }

    @Test
    void automaticallyRecordedDueExpenseNotifiesWholeFamilyIncludingPlanner() {
        Family family = new Family("Nhà mình");
        User mom = mock(User.class);
        User dad = mock(User.class);
        when(mom.getId()).thenReturn(1L);
        when(dad.getId()).thenReturn(2L);
        FamilyMembershipRepository memberships = mock(FamilyMembershipRepository.class);
        when(memberships.findMembers(10L)).thenReturn(List.of(
                new FamilyMembership(family, mom, "OWNER"), new FamilyMembership(family, dad, "MEMBER")));
        PushService push = mock(PushService.class);
        when(push.sendToUsers(any(), any())).thenReturn(new PushService.SendResult(2, 0));
        var event = new TransactionCreatedEvent(
                99L, 10L, 1L, "Mẹ", TransactionType.EXPENSE, new BigDecimal("5000000"), "Giáo dục", null, "Học phí kỳ 2", true);

        new TransactionNotifier(memberships, push).onTransactionCreated(event);

        verify(push).sendToUsers(eq(List.of(1L, 2L)), any()); // cả người lập kế hoạch cũng được báo
        var message = TransactionNotifier.toMessage(event);
        assertEquals("Đã tự ghi khoản chi đến hạn", message.title());
        assertEquals("-5.000.000 ₫ · Giáo dục — Học phí kỳ 2 (do Mẹ lên kế hoạch)", message.body());
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }
}
