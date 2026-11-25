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
import vn.nhaminh.api.user.User;

/** Một lần gửi vào (DEPOSIT) hoặc rút ra (WITHDRAW) khỏi mục tiết kiệm. */
@Entity
@Table(name = "savings_contributions")
public class SavingsContribution {
    public enum Type { DEPOSIT, WITHDRAW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private SavingsGoal goal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(length = 255)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SavingsContribution() {}

    public SavingsContribution(SavingsGoal goal, User createdBy, Type type, BigDecimal amount, LocalDate occurredOn, String note) {
        this.goal = goal;
        this.createdBy = createdBy;
        this.type = type;
        this.amount = amount;
        this.occurredOn = occurredOn;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public SavingsGoal getGoal() { return goal; }
    public Type getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getNote() { return note; }
}
