package vn.nhaminh.api.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Cặp khóa VAPID của server (một dòng duy nhất, id = 1). Tự sinh lần đầu và giữ nguyên;
 * nếu đổi khóa, mọi thiết bị phải bật lại thông báo.
 */
@Entity
@Table(name = "push_vapid_keys")
public class VapidKey {
    @Id
    private Long id;

    /** Khóa công khai dạng điểm không nén, base64url (gửi cho trình duyệt). */
    @Column(name = "public_key", nullable = false, length = 255)
    private String publicKey;

    /** Khóa bí mật PKCS#8, base64url. */
    @Column(name = "private_key", nullable = false, length = 512)
    private String privateKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected VapidKey() {}

    public VapidKey(String publicKey, String privateKey) {
        this.id = 1L;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.createdAt = Instant.now();
    }

    public String getPublicKey() { return publicKey; }
    public String getPrivateKey() { return privateKey; }
}
