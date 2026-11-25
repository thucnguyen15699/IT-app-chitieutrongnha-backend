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

/** Mục tiết kiệm của gia đình, ví dụ "Du lịch Đà Nẵng" với mục đích "Du lịch". */
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Column(nullable = false, length = 100)
    private String name;

    /** Mục đích: Du lịch, Học tập, Mua nhà... (chữ tự do, giao diện gợi ý sẵn). */
    @Column(nullable = false, length = 60)
    private String purpose;

    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SavingsGoal() {}

    public SavingsGoal(Family family, String name, String purpose, BigDecimal targetAmount, LocalDate deadline) {
        this.family = family;
        this.name = name;
        this.purpose = purpose;
        this.targetAmount = targetAmount;
        this.deadline = deadline;
        this.createdAt = Instant.now();
    }

    public void update(String name, String purpose, BigDecimal targetAmount, LocalDate deadline) {
        this.name = name;
        this.purpose = purpose;
        this.targetAmount = targetAmount;
        this.deadline = deadline;
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public String getName() { return name; }
    public String getPurpose() { return purpose; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public LocalDate getDeadline() { return deadline; }
}
