package vn.nhaminh.api.planning;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PlanningDtos {
    private PlanningDtos() {}

    // ----- Khoản sắp phải chi -----

    public record PlannedExpenseRequest(
            @NotNull Long categoryId,
            Long targetId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 500) String note,
            @NotNull LocalDate dueOn) {}

    public record PlannedExpenseView(
            Long id,
            Long categoryId,
            String categoryName,
            Long targetId,
            String targetName,
            BigDecimal amount,
            String note,
            LocalDate dueOn,
            boolean overdue) {}

    // ----- Tiết kiệm -----

    public record SavingsGoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 60) String purpose,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal targetAmount,
            LocalDate deadline) {}

    public record ContributionRequest(
            @NotNull SavingsContribution.Type type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull LocalDate occurredOn,
            @Size(max = 255) String note) {}

    public record ContributionView(
            Long id, SavingsContribution.Type type, BigDecimal amount, LocalDate occurredOn, String note) {}

    public record SavingsGoalView(
            Long id,
            String name,
            String purpose,
            BigDecimal targetAmount,
            BigDecimal savedAmount,
            LocalDate deadline,
            List<ContributionView> recentContributions) {}

    // ----- Tổng hợp cho các ô trên Dashboard -----

    /**
     * pendingTotal: tổng các khoản sắp phải chi chưa chi.
     * savingsNetInMonth: tiền gửi tiết kiệm trừ tiền rút trong tháng (tiền đã rời khỏi ví).
     * totalSaved: tổng số dư của mọi mục tiết kiệm.
     * goldSpentInMonth: tiền mua vàng trong tháng (cũng là tiền đã rời khỏi ví).
     */
    public record PlanningSummary(
            BigDecimal pendingTotal,
            long pendingCount,
            BigDecimal savingsNetInMonth,
            BigDecimal totalSaved,
            BigDecimal goldSpentInMonth) {}
}
