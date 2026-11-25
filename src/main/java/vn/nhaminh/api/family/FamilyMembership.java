package vn.nhaminh.api.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import vn.nhaminh.api.user.User;

@Entity
@Table(name = "family_memberships", uniqueConstraints = {
        @UniqueConstraint(name = "uk_family_member", columnNames = {"family_id", "user_id"})
})
public class FamilyMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    protected FamilyMembership() {}

    public FamilyMembership(Family family, User user, String role) {
        this.family = family;
        this.user = user;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public User getUser() { return user; }
    public String getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }

    public void promoteToOwner() {
        this.role = "OWNER";
    }
}
