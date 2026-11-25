package vn.nhaminh.api.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import vn.nhaminh.api.user.User;

/**
 * Lời mời vào gia đình, gửi theo email và/hoặc số điện thoại.
 * Người được mời thấy lời mời khi đăng nhập bằng tài khoản có email (đã xác minh) hoặc SĐT trùng khớp.
 */
@Entity
@Table(name = "family_invitations")
public class FamilyInvitation {
    public enum Status { PENDING, ACCEPTED, DECLINED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    /** Email người được mời, chữ thường. */
    @Column(length = 254)
    private String email;

    /** SĐT người được mời, đã chuẩn hóa (vd. 0912345678). */
    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by")
    private User respondedBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected FamilyInvitation() {}

    public FamilyInvitation(Family family, User invitedBy, String email, String phoneNumber, String message) {
        this.family = family;
        this.invitedBy = invitedBy;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.message = message;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void respond(Status newStatus, User user) {
        this.status = newStatus;
        this.respondedBy = user;
        this.respondedAt = Instant.now();
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        this.respondedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public User getInvitedBy() { return invitedBy; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getMessage() { return message; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
