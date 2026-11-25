package vn.nhaminh.api.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import vn.nhaminh.api.user.User;

/** Một thiết bị (điện thoại/trình duyệt) đã bật nhận thông báo. Một người có thể có nhiều thiết bị. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Địa chỉ máy chủ push (Apple/Google/...) cấp cho thiết bị. */
    @Column(nullable = false, length = 2048)
    private String endpoint;

    /** SHA-256 của endpoint, để đánh chỉ mục duy nhất (endpoint quá dài để làm khóa). */
    @Column(name = "endpoint_hash", nullable = false, length = 64, unique = true)
    private String endpointHash;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 64)
    private String auth;

    /** Tên thiết bị để người dùng nhận ra, vd "iPhone", "Android". */
    @Column(name = "device_label", length = 100)
    private String deviceLabel;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    protected PushSubscription() {}

    public PushSubscription(User user, String endpoint, String endpointHash, String p256dh, String auth, String deviceLabel) {
        this.user = user;
        this.endpoint = endpoint;
        this.endpointHash = endpointHash;
        this.p256dh = p256dh;
        this.auth = auth;
        this.deviceLabel = deviceLabel;
        this.createdAt = Instant.now();
    }

    /** Cùng thiết bị đăng ký lại (có thể bằng tài khoản khác hoặc khóa mới). */
    public void refresh(User user, String p256dh, String auth, String deviceLabel) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
        this.deviceLabel = deviceLabel;
    }

    public void markSuccess() {
        this.lastSuccessAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public String getDeviceLabel() { return deviceLabel; }
}
