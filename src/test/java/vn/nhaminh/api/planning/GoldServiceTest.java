package vn.nhaminh.api.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.planning.GoldDtos.GoldPriceRequest;
import vn.nhaminh.api.user.User;

@ExtendWith(MockitoExtension.class)
class GoldServiceTest {
    @Mock private CurrentFamilyService currentFamily;
    @Mock private GoldPurchaseRepository purchaseRepository;
    @Mock private GoldPriceRepository priceRepository;
    @InjectMocks private GoldService goldService;

    private final Family family = new Family("Nhà mình");
    private final User user = new User("me", "Mẹ", null, null, "x", true);

    private GoldPurchase purchase(String chi, String cost) {
        return new GoldPurchase(family, user, LocalDate.now().minusDays(4), new BigDecimal(chi), new BigDecimal(cost), null);
    }

    @Test
    void twoChiBoughtFor30MillionLoses2MillionWhenPriceIs14Million() {
        var view = GoldService.toView(purchase("2", "30000000"), new BigDecimal("14000000"));

        assertEquals(new BigDecimal("15000000"), view.costPerChi());
        assertEquals(new BigDecimal("28000000"), view.currentValue());
        assertEquals(new BigDecimal("-2000000"), view.profit());
        assertEquals(new BigDecimal("-6.7"), view.profitPercent());
    }

    @Test
    void halfChiShowsProfitWhenPriceRises() {
        var view = GoldService.toView(purchase("0.5", "7000000"), new BigDecimal("15500000"));

        assertEquals(new BigDecimal("7750000"), view.currentValue());
        assertEquals(new BigDecimal("750000"), view.profit());
        assertEquals(new BigDecimal("10.7"), view.profitPercent());
    }

    @Test
    void withoutAnyPriceProfitIsUnknown() {
        var view = GoldService.toView(purchase("2", "30000000"), null);

        assertNull(view.currentValue());
        assertNull(view.profit());
    }

    @Test
    void overviewUsesLatestSavedPriceAndSumsAllPurchases() {
        when(currentFamily.findFamily("me")).thenReturn(family);
        when(priceRepository.findFirstByFamily_IdOrderByPriceDateDescIdDesc(null)).thenReturn(Optional.of(
                new GoldPrice(family, user, LocalDate.now().minusDays(3), new BigDecimal("14000000"))));
        when(purchaseRepository.findByFamily_IdOrderByPurchasedOnDescIdDesc(null))
                .thenReturn(List.of(purchase("2", "30000000"), purchase("1", "13000000")));
        when(priceRepository.findTop10ByFamily_IdOrderByPriceDateDesc(null)).thenReturn(List.of());

        var overview = goldService.overview("me");

        assertEquals(LocalDate.now().minusDays(3), overview.currentPrice().priceDate()); // hôm nay chưa nhập → giá lần trước
        assertEquals(new BigDecimal("3"), overview.totals().quantityChi());
        assertEquals(new BigDecimal("43000000"), overview.totals().totalCost());
        assertEquals(new BigDecimal("42000000"), overview.totals().currentValue());
        assertEquals(new BigDecimal("-1000000"), overview.totals().profit());
    }

    @Test
    void enteringPriceAgainForSameDayOverwritesIt() {
        GoldPrice today = new GoldPrice(family, user, LocalDate.now(), new BigDecimal("14000000"));
        when(currentFamily.findUser("me")).thenReturn(user);
        when(currentFamily.findFamily(user)).thenReturn(family);
        when(currentFamily.findFamily("me")).thenReturn(family);
        when(priceRepository.findByFamily_IdAndPriceDate(null, LocalDate.now())).thenReturn(Optional.of(today));
        when(priceRepository.findFirstByFamily_IdOrderByPriceDateDescIdDesc(null)).thenReturn(Optional.of(today));

        goldService.setPrice("me", new GoldPriceRequest(LocalDate.now(), new BigDecimal("14500000")));

        assertEquals(new BigDecimal("14500000"), today.getPricePerChi());
        verify(priceRepository, never()).save(any());
    }

    @Test
    void rejectsPriceDatedInTheFuture() {
        when(currentFamily.findUser("me")).thenReturn(user);
        when(currentFamily.findFamily(user)).thenReturn(mock(Family.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> goldService.setPrice("me", new GoldPriceRequest(LocalDate.now().plusDays(1), new BigDecimal("14000000"))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
