package vn.nhaminh.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import vn.nhaminh.api.auth.Role;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 254)
    private String email;

    @Column(length = 30)
    private String phoneNumber;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean emailVerified;

    /**
     * Email mới đang chờ xác minh. Email hiện tại vẫn dùng bình thường cho tới khi
     * người dùng bấm liên kết xác minh, lúc đó pendingEmail mới thay thế email.
     */
    @Column(name = "pending_email", length = 254)
    private String pendingEmail;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {}

    public User(
            String username,
            String name,
            String email,
            String phoneNumber,
            String passwordHash,
            boolean emailVerified) {
        this.username = username.toLowerCase(java.util.Locale.ROOT);
        this.name = name;
        this.email = email == null ? null : email.toLowerCase(java.util.Locale.ROOT);
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.emailVerified = emailVerified;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPendingEmail() { return pendingEmail; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }

    public void addRole(Role role) {
        roles.add(role);
    }

    /** Bấm liên kết xác minh: nếu đang đổi email thì email mới chính thức thay email cũ. */
    public void verifyEmail() {
        if (pendingEmail != null) {
            email = pendingEmail;
            pendingEmail = null;
        }
        emailVerified = true;
    }

    /** Địa chỉ cần gửi thư xác minh: email đang chờ, hoặc email hiện tại nếu chưa xác minh. */
    public String emailToVerify() {
        return pendingEmail != null ? pendingEmail : email;
    }

    public void updateName(String newName) {
        this.name = newName;
    }

    public void updatePhoneNumber(String newPhoneNumber) {
        this.phoneNumber = newPhoneNumber;
    }

    /**
     * Yêu cầu dùng email mới. Nếu email hiện tại chưa xác minh (vd. vừa đăng ký gõ nhầm)
     * thì thay luôn, vì chưa có gì để giữ lại; ngược lại chờ xác minh.
     */
    public void requestEmailChange(String newEmail) {
        String normalized = newEmail.toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals(email)) {
            pendingEmail = null;
            return;
        }
        if (email != null && !emailVerified) {
            email = normalized;
            pendingEmail = null;
        } else {
            pendingEmail = normalized;
        }
    }
}
