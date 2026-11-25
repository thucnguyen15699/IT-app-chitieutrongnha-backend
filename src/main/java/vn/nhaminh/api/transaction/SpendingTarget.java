package vn.nhaminh.api.transaction;

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
import vn.nhaminh.api.family.Family;

/**
 * Đối tượng hưởng khoản chi: cả nhà, bố, mẹ, con...
 * family == null là đối tượng mặc định dùng chung cho mọi gia đình.
 */
@Entity
@Table(name = "spending_targets")
public class SpendingTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SpendingTarget() {}

    public SpendingTarget(Family family, String name, int sortOrder) {
        this.family = family;
        this.name = name;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
}
