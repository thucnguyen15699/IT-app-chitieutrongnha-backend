package vn.nhaminh.api.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import vn.nhaminh.api.transaction.TransactionDtos.MonthTotal;
import vn.nhaminh.api.transaction.TransactionDtos.StatsGroup;
import vn.nhaminh.api.transaction.TransactionDtos.StatsPart;
import vn.nhaminh.api.transaction.TransactionDtos.StatsView;

/** Gom nhóm giao dịch thành số liệu thống kê. Không truy cập DB, dễ test. */
final class TransactionStats {
    static final String WHOLE_FAMILY = "Cả nhà";
    static final String OTHER_CATEGORY = "Khác";

    private TransactionStats() {}

    static StatsView build(String period, List<FamilyTransaction> transactions, boolean includeMonths) {
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        List<FamilyTransaction> expenses = new ArrayList<>();
        BigDecimal[] monthIncome = new BigDecimal[12];
        BigDecimal[] monthExpense = new BigDecimal[12];
        java.util.Arrays.fill(monthIncome, BigDecimal.ZERO);
        java.util.Arrays.fill(monthExpense, BigDecimal.ZERO);

        for (FamilyTransaction transaction : transactions) {
            int monthIndex = transaction.getOccurredAt().getMonthValue() - 1;
            if (transaction.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(transaction.getAmount());
                monthIncome[monthIndex] = monthIncome[monthIndex].add(transaction.getAmount());
            } else {
                totalExpense = totalExpense.add(transaction.getAmount());
                monthExpense[monthIndex] = monthExpense[monthIndex].add(transaction.getAmount());
                expenses.add(transaction);
            }
        }

        List<MonthTotal> months = new ArrayList<>();
        if (includeMonths) {
            for (int i = 0; i < 12; i++) {
                months.add(new MonthTotal(i + 1, monthIncome[i], monthExpense[i]));
            }
        }

        return new StatsView(
                period,
                totalIncome,
                totalExpense,
                expenses.size(),
                group(expenses, TransactionStats::categoryName, TransactionStats::targetName),
                group(expenses, TransactionStats::targetName, TransactionStats::categoryName),
                months);
    }

    /** Gom theo "primary", mỗi nhóm kèm phân chia theo "secondary"; sắp xếp tiền giảm dần. */
    private static List<StatsGroup> group(
            List<FamilyTransaction> expenses,
            Function<FamilyTransaction, String> primary,
            Function<FamilyTransaction, String> secondary) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        for (FamilyTransaction transaction : expenses) {
            Accumulator accumulator = groups.computeIfAbsent(primary.apply(transaction), key -> new Accumulator());
            accumulator.amount = accumulator.amount.add(transaction.getAmount());
            accumulator.count++;
            accumulator.parts.merge(secondary.apply(transaction), transaction.getAmount(), BigDecimal::add);
        }
        return groups.entrySet().stream()
                .map(entry -> new StatsGroup(
                        entry.getKey(),
                        entry.getValue().amount,
                        entry.getValue().count,
                        entry.getValue().parts.entrySet().stream()
                                .map(part -> new StatsPart(part.getKey(), part.getValue()))
                                .sorted(Comparator.comparing(StatsPart::amount).reversed())
                                .toList()))
                .sorted(Comparator.comparing(StatsGroup::amount).reversed())
                .toList();
    }

    private static String categoryName(FamilyTransaction transaction) {
        return transaction.getCategory() == null ? OTHER_CATEGORY : transaction.getCategory().getName();
    }

    /** Khoản chi không chọn đối tượng được tính là "Cả nhà" (gộp với đối tượng mặc định cùng tên). */
    private static String targetName(FamilyTransaction transaction) {
        return transaction.getTarget() == null ? WHOLE_FAMILY : transaction.getTarget().getName();
    }

    private static final class Accumulator {
        BigDecimal amount = BigDecimal.ZERO;
        long count;
        final Map<String, BigDecimal> parts = new LinkedHashMap<>();
    }
}
