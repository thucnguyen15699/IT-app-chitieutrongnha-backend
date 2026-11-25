package vn.nhaminh.api.planning;

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
import java.time.LocalDate;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.transaction.ExpenseCategory;
import vn.nhaminh.api.transaction.FamilyTransaction;
import vn.nhaminh.api.transaction.SpendingTarget;
import vn.nhaminh.api.user.User;

/** Khoản đã biết trước sẽ phải chi trong tương lai (học phí, tiền nhà, đám cưới...). */
@Entity
@Table(name = "planned_expenses")
public class PlannedExpense {
    public enum Status { PENDING, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private SpendingTarget target;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 500, nullable = false)
    private String description;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** Giao dịch chi thật được tạo ra khi bấm "Đã chi". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_transaction_id")
    private FamilyTransaction paidTransaction;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PlannedExpense() {}

    public PlannedExpense(
            Family family,
            ExpenseCategory category,
            SpendingTarget target,
            User createdBy,
            BigDecimal amount,
            String description,
            LocalDate dueOn) {
        this.family = family;
        this.category = category;
        this.target = target;
        this.createdBy = createdBy;
        this.amount = amount;
        this.description = description;
        this.dueOn = dueOn;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void update(ExpenseCategory category, SpendingTarget target, BigDecimal amount, String description, LocalDate dueOn) {
        this.category = category;
        this.target = target;
        this.amount = amount;
        this.description = description;
        this.dueOn = dueOn;
    }

    public void markPaid(FamilyTransaction transaction) {
        this.status = Status.PAID;
        this.paidTransaction = transaction;
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public ExpenseCategory getCategory() { return category; }
    public SpendingTarget getTarget() { return target; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDate getDueOn() { return dueOn; }
    public Status getStatus() { return status; }
    public User getCreatedBy() { return createdBy; }
}
