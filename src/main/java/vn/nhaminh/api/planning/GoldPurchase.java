package vn.nhaminh.api.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.user.User;

/** Một lần mua vàng: ví dụ 21/11 mua 2 chỉ hết 30.000.000 đ. */
@Entity
@Table(name = "gold_purchases")
public class GoldPurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "purchased_on", nullable = false)
    private LocalDate purchasedOn;

    /** Số chỉ (cho phép lẻ, vd. 0.5). */
    @Column(name = "quantity_chi", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantityChi;

    /** Tổng số tiền đã trả cho lần mua này. */
    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 255)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GoldPurchase() {}

    public GoldPurchase(Family family, User createdBy, LocalDate purchasedOn, BigDecimal quantityChi,
            BigDecimal totalCost, String note) {
        this.family = family;
        this.createdBy = createdBy;
        this.purchasedOn = purchasedOn;
        this.quantityChi = quantityChi;
        this.totalCost = totalCost;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public void update(LocalDate purchasedOn, BigDecimal quantityChi, BigDecimal totalCost, String note) {
        this.purchasedOn = purchasedOn;
        this.quantityChi = quantityChi;
        this.totalCost = totalCost;
        this.note = note;
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public LocalDate getPurchasedOn() { return purchasedOn; }
    public BigDecimal getQuantityChi() { return quantityChi; }
    public BigDecimal getTotalCost() { return totalCost; }
    public String getNote() { return note; }
}
