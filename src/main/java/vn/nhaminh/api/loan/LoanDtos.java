package vn.nhaminh.api.loan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LoanDtos {
    private LoanDtos() {}

    public record LoanRequest(
            @NotNull Loan.LenderType lenderType,
            @NotBlank @Size(max = 100) String lenderName,
            @NotNull @DecimalMin("1") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull LocalDate borrowedOn,
            @NotNull Loan.RepaymentMode repaymentMode,
            @DecimalMin("1") @Digits(integer = 13, fraction = 2) BigDecimal installmentAmount,
            LocalDate dueOn,
            @Size(max = 500) String note) {}

    public record RepaymentRequest(
            @NotNull @DecimalMin("1") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull LocalDate paidOn,
            @Size(max = 255) String note) {}

    public record RepaymentView(Long id, BigDecimal amount, LocalDate paidOn, String note) {}

    /** remaining = amount − repaid; paidOff khi đã trả hết. */
    public record LoanView(
            Long id,
            Loan.LenderType lenderType,
            String lenderName,
            BigDecimal amount,
            LocalDate borrowedOn,
            Loan.RepaymentMode repaymentMode,
            BigDecimal installmentAmount,
            LocalDate dueOn,
            String note,
            BigDecimal repaid,
            BigDecimal remaining,
            boolean paidOff,
            List<RepaymentView> repayments) {}

    /** totalDebt: tổng tiền còn nợ của mọi khoản vay. */
    public record LoanOverview(
            List<LoanView> loans,
            BigDecimal totalBorrowed,
            BigDecimal totalRepaid,
            BigDecimal totalDebt) {}
}
