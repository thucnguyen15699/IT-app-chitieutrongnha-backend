package vn.nhaminh.api.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TransactionDtos {
    private TransactionDtos() {}

    public record TransactionRequest(
            @NotNull TransactionType type,
            @NotNull Long categoryId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 500) String note,
            @NotNull LocalDate occurredOn,
            Long targetId) {}

    /** custom = true nếu danh mục do gia đình tự tạo (xóa được khi chưa dùng). */
    public record CategoryView(Long id, String name, TransactionType type, boolean custom) {}

    public record CreateCategoryRequest(@NotBlank @Size(max = 100) String name, @NotNull TransactionType type) {}

    /** custom = true nếu đối tượng do gia đình tự thêm (không phải mặc định). */
    public record TargetView(Long id, String name, boolean custom) {}

    public record CreateTargetRequest(@NotBlank @Size(max = 60) String name) {}

    public record TransactionView(
            Long id,
            TransactionType type,
            Long categoryId,
            String categoryName,
            Long targetId,
            String targetName,
            BigDecimal amount,
            String note,
            LocalDate occurredOn) {}

    public record TransactionList(List<TransactionView> transactions) {}

    /** Một phần nhỏ bên trong nhóm, ví dụ trong mục "Ăn uống" thì "Con" chiếm bao nhiêu. */
    public record StatsPart(String name, BigDecimal amount) {}

    /** Một khoản chi trong nhóm (xem theo tháng). otherName: chiều còn lại (mục → ai, ai → mục). */
    public record StatsItem(Long id, LocalDate date, BigDecimal amount, String note, String otherName) {}

    /** Tổng chi của nhóm trong một tháng (xem theo năm). */
    public record StatsMonthPart(int month, BigDecimal amount, long count) {}

    /**
     * Một nhóm thống kê (một mục chi hoặc một đối tượng), kèm phân chia theo chiều còn lại.
     * Xem theo tháng: items = từng khoản chi (mới nhất trước), months rỗng.
     * Xem theo năm: months = các tháng có chi (tháng 1 → 12), items rỗng.
     */
    public record StatsGroup(
            String name,
            BigDecimal amount,
            long count,
            List<StatsPart> breakdown,
            List<StatsMonthPart> months,
            List<StatsItem> items) {}

    public record MonthTotal(int month, BigDecimal income, BigDecimal expense) {}

    /**
     * period: "2026-11" (theo tháng) hoặc "2026" (theo năm).
     * months chỉ có dữ liệu khi xem theo năm (đủ 12 tháng).
     */
    public record StatsView(
            String period,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            long expenseCount,
            List<StatsGroup> byCategory,
            List<StatsGroup> byTarget,
            List<MonthTotal> months) {}
}
