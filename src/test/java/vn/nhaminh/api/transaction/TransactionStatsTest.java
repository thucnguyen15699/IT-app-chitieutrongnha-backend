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
    void monthlyGroupListsEachExpenseNewestFirst() {
        var lunch = new FamilyTransaction(family, food, child, user, TransactionType.EXPENSE,
                new BigDecimal("80000"), "Cơm trưa", LocalDate.of(2026, 11, 3).atStartOfDay());
        var market = new FamilyTransaction(family, food, null, user, TransactionType.EXPENSE,
                new BigDecimal("300000"), "Đi chợ cuối tuần", LocalDate.of(2026, 11, 20).atStartOfDay());

        var foodGroup = TransactionStats.build("2026-11", List.of(lunch, market), false).byCategory().get(0);

        assertEquals(2, foodGroup.items().size());
        assertEquals(LocalDate.of(2026, 11, 20), foodGroup.items().get(0).date());
        assertEquals("Đi chợ cuối tuần", foodGroup.items().get(0).note());
        assertEquals("Cả nhà", foodGroup.items().get(0).otherName());
        assertEquals("Con", foodGroup.items().get(1).otherName());
        assertEquals(0, foodGroup.months().size());
    }

    @Test
    void yearlyGroupHasTotalPerMonthOnlyForMonthsWithSpending() {
        var stats = TransactionStats.build("2026", List.of(
                expense(food, null, "6000000", 1),
                expense(food, child, "4000000", 1),
                expense(food, null, "7500000", 3),
                expense(shopping, null, "200000", 2)), true);

        var foodGroup = stats.byCategory().get(0);
        assertEquals("Ăn uống", foodGroup.name());
        assertEquals(2, foodGroup.months().size()); // tháng 2 không ăn uống → không có dòng
        assertEquals(1, foodGroup.months().get(0).month());
        assertEquals(new BigDecimal("10000000"), foodGroup.months().get(0).amount());
        assertEquals(2, foodGroup.months().get(0).count());
        assertEquals(3, foodGroup.months().get(1).month());
        assertEquals(0, foodGroup.items().size());
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
