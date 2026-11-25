package vn.nhaminh.api.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.user.User;

@DataJpaTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:transactiontest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class TransactionRepositoryTest {
    @Autowired private ExpenseCategoryRepository categoryRepository;
    @Autowired private FamilyTransactionRepository transactionRepository;
    @Autowired private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void storesAndListsFamilyTransactionsAndCategoriesByMonthAndType() {
        Family family = entityManager.persistAndFlush(new Family("Nhà mình"));
        User user = entityManager.persistAndFlush(
                new User("member", "Thành viên", null, null, "encoded", true));
        ExpenseCategory expense = entityManager.persistAndFlush(
                new ExpenseCategory(null, "Đi chợ", TransactionType.EXPENSE));
        entityManager.persistAndFlush(new ExpenseCategory(null, "Lương", TransactionType.INCOME));
        LocalDate occurredOn = LocalDate.now().minusDays(1);
        entityManager.persistAndFlush(new FamilyTransaction(
                family, expense, user, TransactionType.EXPENSE,
                new BigDecimal("100000"), "Mua thịt", occurredOn.atStartOfDay()));
        entityManager.flush();

        var transactions = transactionRepository
                .findByFamily_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                        family.getId(),
                        YearMonth.from(occurredOn).atDay(1).atStartOfDay(),
                        YearMonth.from(occurredOn).plusMonths(1).atDay(1).atStartOfDay());
        var categories = categoryRepository.findAvailableCategories(family.getId(), TransactionType.EXPENSE);

        assertEquals(1, transactions.size());
        assertEquals("Mua thịt", transactions.getFirst().getDescription());
        assertEquals(1, categories.size());
        assertEquals("Đi chợ", categories.getFirst().getName());

        var forStats = transactionRepository.findForStats(
                family.getId(),
                occurredOn.withDayOfYear(1).atStartOfDay(),
                occurredOn.withDayOfYear(1).plusYears(1).atStartOfDay());
        assertEquals(1, forStats.size());
        assertEquals("Đi chợ", forStats.getFirst().getCategory().getName());
    }
}
