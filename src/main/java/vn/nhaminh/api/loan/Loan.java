package vn.nhaminh.api.loan;

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
import vn.nhaminh.api.user.User;

/** Một khoản vay: ví dụ 01/11 vay bố mẹ 5.000.000 đ để sửa xe, trả dần mỗi tháng 1.000.000 đ. */
@Entity
@Table(name = "loans")
public class Loan {
    /** Vay ai. */
    public enum LenderType { FRIEND, PARENT, SIBLING, RELATIVE, COLLEAGUE, BANK, OTHER }

    /** Cách trả: trả dần nhiều lần, hoặc trả một lần. */
    public enum RepaymentMode { INSTALLMENT, LUMP_SUM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "lender_type", nullable = false, length = 20)
    private LenderType lenderType;

    /** Tên người cho vay, vd "Anh Tuấn", "Bố mẹ". */
    @Column(name = "lender_name", nullable = false, length = 100)
    private String lenderName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "borrowed_on", nullable = false)
    private LocalDate borrowedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_mode", nullable = false, length = 20)
    private RepaymentMode repaymentMode;

    /** Số tiền dự định trả mỗi lần (chỉ dùng khi trả dần). */
    @Column(name = "installment_amount", precision = 15, scale = 2)
    private BigDecimal installmentAmount;

    /** Hạn trả hết (không bắt buộc). */
    @Column(name = "due_on")
    private LocalDate dueOn;

    /** Vay để làm gì, cho dễ nhớ. */
    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Loan() {}

    public Loan(Family family, User createdBy, LenderType lenderType, String lenderName, BigDecimal amount,
            LocalDate borrowedOn, RepaymentMode repaymentMode, BigDecimal installmentAmount, LocalDate dueOn, String note) {
        this.family = family;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        update(lenderType, lenderName, amount, borrowedOn, repaymentMode, installmentAmount, dueOn, note);
    }

    public void update(LenderType lenderType, String lenderName, BigDecimal amount, LocalDate borrowedOn,
            RepaymentMode repaymentMode, BigDecimal installmentAmount, LocalDate dueOn, String note) {
        this.lenderType = lenderType;
        this.lenderName = lenderName;
        this.amount = amount;
        this.borrowedOn = borrowedOn;
        this.repaymentMode = repaymentMode;
        this.installmentAmount = repaymentMode == RepaymentMode.INSTALLMENT ? installmentAmount : null;
        this.dueOn = dueOn;
        this.note = note;
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public LenderType getLenderType() { return lenderType; }
    public String getLenderName() { return lenderName; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getBorrowedOn() { return borrowedOn; }
    public RepaymentMode getRepaymentMode() { return repaymentMode; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public LocalDate getDueOn() { return dueOn; }
    public String getNote() { return note; }
}
