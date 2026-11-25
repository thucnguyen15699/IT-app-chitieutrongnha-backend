package vn.nhaminh.api.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

/** Gửi thông báo Web Push tới điện thoại/trình duyệt của người dùng (iOS 16.4+, Android, máy tính). */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /**
     * Chỉ gửi tới máy chủ push chính thức. Endpoint do trình duyệt khai báo; nếu không giới hạn,
     * kẻ xấu có thể bắt server gửi request tới địa chỉ tùy ý (SSRF).
     */
    private static final List<String> ALLOWED_PUSH_HOSTS = List.of(
            "fcm.googleapis.com",          // Chrome, Android, Edge (mới)
            "push.apple.com",              // Safari, iOS/iPadOS (web.push.apple.com)
            "push.services.mozilla.com",   // Firefox
            "notify.windows.com");         // Edge (cũ)

    public record PushMessage(String title, String body, String url, String tag) {}

    public record SubscribeRequest(String endpoint, String p256dh, String auth, String deviceLabel) {}

    public record SendResult(int sent, int removed) {}

    private final PushSubscriptionRepository subscriptionRepository;
    private final VapidKeyRepository vapidKeyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String subject;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile LoadedKeys keys;

    private record LoadedKeys(String publicKeyBase64, byte[] publicKey, PrivateKey privateKey) {}

    public PushService(
            PushSubscriptionRepository subscriptionRepository,
            VapidKeyRepository vapidKeyRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Value("${app.push.subject:}") String subject,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.vapidKeyRepository = vapidKeyRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        // Apple yêu cầu "sub" là mailto: hoặc https:// thật (không chấp nhận localhost).
        this.subject = !subject.isBlank() ? subject
                : frontendUrl.startsWith("https://") ? frontendUrl : "mailto:thongbao@nhaminh.app";
    }

    // ================= Đăng ký thiết bị =================

    public String publicKey() {
        return keys().publicKeyBase64();
    }

    @Transactional
    public void subscribe(String username, SubscribeRequest request) {
        User user = findUser(username);
        validateEndpoint(request.endpoint());
        try {
            if (WebPushCrypto.fromBase64Url(request.p256dh()).length != 65
                    || WebPushCrypto.fromBase64Url(request.auth()).length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin đăng ký thông báo không hợp lệ.");
        }
        String label = request.deviceLabel() == null ? null
                : request.deviceLabel().substring(0, Math.min(100, request.deviceLabel().length()));
        String hash = sha256(request.endpoint());
        subscriptionRepository.findByEndpointHash(hash).ifPresentOrElse(
                existing -> existing.refresh(user, request.p256dh(), request.auth(), label),
                () -> subscriptionRepository.save(new PushSubscription(
                        user, request.endpoint(), hash, request.p256dh(), request.auth(), label)));
    }

    @Transactional
    public void unsubscribe(String username, String endpoint) {
        User user = findUser(username);
        subscriptionRepository.findByEndpointHash(sha256(endpoint))
                .filter(subscription -> subscription.getUser().getId().equals(user.getId()))
                .ifPresent(subscriptionRepository::delete);
    }

    @Transactional
    public SendResult sendTest(String username) {
        User user = findUser(username);
        if (subscriptionRepository.countByUser_Id(user.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiết bị này chưa bật thông báo.");
        }
        return sendToUsers(List.of(user.getId()), new PushMessage(
                "NhaMinh", "Thông báo đã hoạt động trên thiết bị của bạn 🎉", "/#/tong-quan", "test"));
    }

    // ================= Gửi =================

    /** Gửi tới mọi thiết bị của các người dùng. Thiết bị đã hủy (404/410) sẽ bị xóa. */
    @Transactional
    public SendResult sendToUsers(Collection<Long> userIds, PushMessage message) {
        if (userIds.isEmpty()) return new SendResult(0, 0);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        int sent = 0;
        int removed = 0;
        for (PushSubscription subscription : subscriptionRepository.findByUser_IdIn(userIds)) {
            int status = deliver(subscription, payload);
            if (status == 404 || status == 410) {
                subscriptionRepository.delete(subscription); // người dùng đã tắt/gỡ app
                removed++;
            } else if (status >= 200 && status < 300) {
                subscription.markSuccess();
                sent++;
            }
        }
        return new SendResult(sent, removed);
    }

    private int deliver(PushSubscription subscription, byte[] payload) {
        try {
            URI endpoint = URI.create(subscription.getEndpoint());
            validateEndpoint(subscription.getEndpoint());
            LoadedKeys vapid = keys();
            byte[] body = WebPushCrypto.encrypt(
                    payload,
                    WebPushCrypto.fromBase64Url(subscription.getP256dh()),
                    WebPushCrypto.fromBase64Url(subscription.getAuth()));
            String audience = endpoint.getScheme() + "://" + endpoint.getHost();
            long expires = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization",
                            WebPushCrypto.vapidAuthorization(audience, subject, vapid.privateKey(), vapid.publicKey(), expires))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300 && response.statusCode() != 404 && response.statusCode() != 410) {
                log.warn("Gửi thông báo thất bại ({}): {}", response.statusCode(), truncate(response.body()));
            }
            return response.statusCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception exception) {
            log.warn("Không gửi được thông báo tới thiết bị #{}: {}", subscription.getId(), exception.getMessage());
            return -1;
        }
    }

    // ================= Hỗ trợ =================

    static void validateEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ nhận thông báo không hợp lệ.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                && uri.getPort() == -1
                && uri.getUserInfo() == null
                && ALLOWED_PUSH_HOSTS.stream().anyMatch(allowedHost -> host.equals(allowedHost) || host.endsWith("." + allowedHost));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dịch vụ thông báo này không được hỗ trợ.");
        }
    }

    /** Lấy khóa VAPID trong DB; lần đầu chưa có thì tạo và lưu lại. */
    private LoadedKeys keys() {
        LoadedKeys loaded = keys;
        if (loaded != null) return loaded;
        synchronized (this) {
            if (keys == null) {
                VapidKey stored = vapidKeyRepository.findById(1L).orElseGet(() -> {
                    KeyPair pair = WebPushCrypto.generateKeyPair();
                    log.info("Tạo khóa VAPID mới cho thông báo đẩy.");
                    return vapidKeyRepository.save(new VapidKey(
                            WebPushCrypto.base64Url(WebPushCrypto.encodePublicKey((ECPublicKey) pair.getPublic())),
                            WebPushCrypto.base64Url(pair.getPrivate().getEncoded())));
                });
                try {
                    keys = new LoadedKeys(
                            stored.getPublicKey(),
                            WebPushCrypto.fromBase64Url(stored.getPublicKey()),
                            WebPushCrypto.decodePrivateKey(WebPushCrypto.fromBase64Url(stored.getPrivateKey())));
                } catch (GeneralSecurityException exception) {
                    throw new IllegalStateException("Khóa VAPID trong database bị hỏng.", exception);
                }
            }
            return keys;
        }
    }

    private User findUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn tồn tại."));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String truncate(String value) {
        return value == null ? "" : value.substring(0, Math.min(200, value.length()));
    }
}
