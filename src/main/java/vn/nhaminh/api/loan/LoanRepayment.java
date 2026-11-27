package vn.nhaminh.api.loan;

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
import vn.nhaminh.api.user.User;

/** Một lần trả nợ cho một khoản vay. */
@Entity
@Table(name = "loan_repayments")
public class LoanRepayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(length = 255)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LoanRepayment() {}

    public LoanRepayment(Loan loan, User createdBy, BigDecimal amount, LocalDate paidOn, String note) {
        this.loan = loan;
        this.createdBy = createdBy;
        this.amount = amount;
        this.paidOn = paidOn;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Loan getLoan() { return loan; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getNote() { return note; }
}
