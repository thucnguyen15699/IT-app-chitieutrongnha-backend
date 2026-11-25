package vn.nhaminh.api.transaction;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.user.User;

@Entity
@Table(name = "family_transactions")
public class FamilyTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ExpenseCategory category;

    /** Đối tượng hưởng khoản chi; null nghĩa là cả nhà (hoặc là khoản thu). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private SpendingTarget target;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected FamilyTransaction() {}

    public FamilyTransaction(
            Family family,
            ExpenseCategory category,
            User createdBy,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDateTime occurredAt) {
        this(family, category, null, createdBy, type, amount, description, occurredAt);
    }

    public FamilyTransaction(
            Family family,
            ExpenseCategory category,
            SpendingTarget target,
            User createdBy,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDateTime occurredAt) {
        this.family = family;
        this.target = target;
        this.category = category;
        this.createdBy = createdBy;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public ExpenseCategory getCategory() { return category; }
    public SpendingTarget getTarget() { return target; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    public void update(
            ExpenseCategory category,
            SpendingTarget target,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDateTime occurredAt) {
        this.category = category;
        this.target = target;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.occurredAt = occurredAt;
    }
}
