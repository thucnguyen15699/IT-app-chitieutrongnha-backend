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
import vn.nhaminh.api.user.User;

@DataJpaTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:goldtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class GoldRepositoryTest {
    @Autowired private GoldPurchaseRepository purchaseRepository;
    @Autowired private GoldPriceRepository priceRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void findsLatestPriceAndMonthlyGoldSpending() {
        Family family = entityManager.persist(new Family("Nhà mình"));
        User user = entityManager.persist(new User("me", "Mẹ", null, null, "x", true));
        LocalDate today = LocalDate.now();
        entityManager.persist(new GoldPrice(family, user, today.minusDays(5), new BigDecimal("15000000")));
        entityManager.persist(new GoldPrice(family, user, today.minusDays(1), new BigDecimal("14000000")));
        entityManager.persist(new GoldPurchase(family, user, today.withDayOfMonth(1), new BigDecimal("2"), new BigDecimal("30000000"), null));
        entityManager.persist(new GoldPurchase(family, user, today.withDayOfMonth(1).minusMonths(1), new BigDecimal("1"), new BigDecimal("13000000"), null));
        entityManager.flush();

        var latest = priceRepository.findFirstByFamily_IdOrderByPriceDateDescIdDesc(family.getId()).orElseThrow();
        assertEquals(today.minusDays(1), latest.getPriceDate());
        assertEquals(2, priceRepository.findTop10ByFamily_IdOrderByPriceDateDesc(family.getId()).size());
        assertEquals(2, purchaseRepository.findByFamily_IdOrderByPurchasedOnDescIdDesc(family.getId()).size());
        assertEquals(0, new BigDecimal("30000000").compareTo(purchaseRepository.sumCostInPeriod(
                family.getId(), today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1))));
    }
}
