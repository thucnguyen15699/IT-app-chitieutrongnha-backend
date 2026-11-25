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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.user.User;

/**
 * Giá vàng (giá 1 chỉ) do gia đình tự nhập, mỗi ngày tối đa một giá.
 * Ngày nào không nhập thì dùng giá của lần nhập gần nhất trước đó.
 */
@Entity
@Table(name = "gold_prices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_gold_price_family_date", columnNames = {"family_id", "price_date"})
})
public class GoldPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "price_per_chi", nullable = false, precision = 15, scale = 2)
    private BigDecimal pricePerChi;

    @Column(nullable = false)
    private Instant createdAt;

    protected GoldPrice() {}

    public GoldPrice(Family family, User createdBy, LocalDate priceDate, BigDecimal pricePerChi) {
        this.family = family;
        this.createdBy = createdBy;
        this.priceDate = priceDate;
        this.pricePerChi = pricePerChi;
        this.createdAt = Instant.now();
    }

    /** Nhập lại giá cho cùng một ngày thì ghi đè giá cũ. */
    public void changePrice(BigDecimal newPrice, User user) {
        this.pricePerChi = newPrice;
        this.createdBy = user;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getPriceDate() { return priceDate; }
    public BigDecimal getPricePerChi() { return pricePerChi; }
}
