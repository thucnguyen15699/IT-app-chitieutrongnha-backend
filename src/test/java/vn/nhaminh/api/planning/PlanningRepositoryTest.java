package vn.nhaminh.api.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.transaction.ExpenseCategory;
import vn.nhaminh.api.transaction.TransactionType;
import vn.nhaminh.api.user.User;

@DataJpaTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:planningtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class PlanningRepositoryTest {
    @Autowired private PlannedExpenseRepository plannedRepository;
    @Autowired private SavingsGoalRepository goalRepository;
    @Autowired private SavingsContributionRepository contributionRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void queriesPlannedExpensesAndSavingsBalances() {
        Family family = entityManager.persistAndFlush(new Family("Nhà mình"));
        User user = entityManager.persistAndFlush(new User("member", "Thành viên", null, null, "encoded", true));
        ExpenseCategory education = entityManager.persistAndFlush(
                new ExpenseCategory(null, "Giáo dục", TransactionType.EXPENSE));
        LocalDate today = LocalDate.now();

        entityManager.persist(new PlannedExpense(family, education, null, user, new BigDecimal("5000000"), "Học phí", today.plusDays(20)));
        entityManager.persist(new PlannedExpense(family, education, null, user, new BigDecimal("2000000"), "Sách vở", today.plusDays(5)));
        SavingsGoal trip = entityManager.persist(new SavingsGoal(family, "Đi Đà Nẵng", "Du lịch", new BigDecimal("20000000"), null));
        entityManager.persist(new SavingsContribution(trip, user, SavingsContribution.Type.DEPOSIT, new BigDecimal("3000000"), today, null));
        entityManager.persist(new SavingsContribution(trip, user, SavingsContribution.Type.WITHDRAW, new BigDecimal("1000000"), today, null));
        entityManager.flush();

        var pending = plannedRepository.findByFamilyAndStatus(family.getId(), PlannedExpense.Status.PENDING);
        assertEquals(2, pending.size());
        assertEquals("Sách vở", pending.getFirst().getDescription()); // hạn gần nhất lên đầu
        assertEquals(0, new BigDecimal("7000000").compareTo(
                plannedRepository.sumByFamilyAndStatus(family.getId(), PlannedExpense.Status.PENDING)));

        var rows = contributionRepository.sumByGoalAndType(family.getId());
        assertEquals(2, rows.size());
        assertEquals(0, new BigDecimal("3000000").compareTo(contributionRepository.sumByFamilyTypeAndPeriod(
                family.getId(), SavingsContribution.Type.DEPOSIT, today.withDayOfMonth(1), today.plusMonths(1).withDayOfMonth(1))));
        assertEquals(1, goalRepository.findByFamily_IdOrderByCreatedAtAsc(family.getId()).size());
        assertEquals(2, contributionRepository.findTop5ByGoal_IdOrderByOccurredOnDescIdDesc(trip.getId()).size());
    }
}
