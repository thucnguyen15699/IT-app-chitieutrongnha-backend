package vn.nhaminh.api.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.user.User;

class TransactionStatsTest {
    private final Family family = new Family("Nhà mình");
    private final User user = new User("member", "Member", null, null, "encoded", true);
    private final ExpenseCategory food = new ExpenseCategory(null, "Ăn uống", TransactionType.EXPENSE);
    private final ExpenseCategory shopping = new ExpenseCategory(null, "Mua sắm", TransactionType.EXPENSE);
    private final ExpenseCategory salary = new ExpenseCategory(null, "Lương", TransactionType.INCOME);
    private final SpendingTarget child = new SpendingTarget(null, "Con", 30);
    private final SpendingTarget wholeFamily = new SpendingTarget(null, "Cả nhà", 0);

    private FamilyTransaction expense(ExpenseCategory category, SpendingTarget target, String amount, int month) {
        return new FamilyTransaction(family, category, target, user, TransactionType.EXPENSE,
                new BigDecimal(amount), "ghi chú", LocalDate.of(2026, month, 5).atStartOfDay());
    }

    @Test
    void groupsExpensesByCategoryAndTargetWithBreakdown() {
        var stats = TransactionStats.build("2026-11", List.of(
                expense(food, wholeFamily, "300000", 11),
                expense(food, child, "100000", 11),
                expense(food, null, "50000", 11),
                expense(shopping, child, "500000", 11),
                new FamilyTransaction(family, salary, user, TransactionType.INCOME,
                        new BigDecimal("2000000"), null, LocalDate.of(2026, 11, 1).atStartOfDay())),
                false);

        assertEquals(new BigDecimal("2000000"), stats.totalIncome());
        assertEquals(new BigDecimal("950000"), stats.totalExpense());
        assertEquals(4, stats.expenseCount());

        // Mua sắm (500k) lớn hơn Ăn uống (450k) nên đứng đầu.
        assertEquals("Mua sắm", stats.byCategory().get(0).name());
        var foodGroup = stats.byCategory().get(1);
        assertEquals(new BigDecimal("450000"), foodGroup.amount());
        assertEquals(3, foodGroup.count());
        // Không chọn đối tượng thì được gộp vào "Cả nhà".
        assertEquals("Cả nhà", foodGroup.breakdown().get(0).name());
        assertEquals(new BigDecimal("350000"), foodGroup.breakdown().get(0).amount());

        var childGroup = stats.byTarget().get(0);
        assertEquals("Con", childGroup.name());
        assertEquals(new BigDecimal("600000"), childGroup.amount());
        assertEquals("Mua sắm", childGroup.breakdown().get(0).name());
        assertEquals(0, stats.months().size());
    }

    @Test
    void yearlyStatsListAllTwelveMonths() {
        var stats = TransactionStats.build("2026", List.of(
                expense(food, null, "100000", 1),
                expense(food, null, "200000", 3)),
                true);

        assertEquals(12, stats.months().size());
        assertEquals(new BigDecimal("100000"), stats.months().get(0).expense());
        assertEquals(BigDecimal.ZERO, stats.months().get(1).expense());
        assertEquals(new BigDecimal("200000"), stats.months().get(2).expense());
    }
}
