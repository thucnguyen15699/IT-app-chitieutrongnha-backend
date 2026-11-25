package vn.nhaminh.api.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.planning.GoldDtos.GoldOverview;
import vn.nhaminh.api.planning.GoldDtos.GoldPriceRequest;
import vn.nhaminh.api.planning.GoldDtos.GoldPriceView;
import vn.nhaminh.api.planning.GoldDtos.GoldPurchaseRequest;
import vn.nhaminh.api.planning.GoldDtos.GoldPurchaseView;
import vn.nhaminh.api.planning.GoldDtos.GoldTotals;
import vn.nhaminh.api.user.User;

/**
 * Mua vàng & lãi/lỗ.
 * Lãi/lỗ mỗi lần mua = số chỉ × giá 1 chỉ hiện tại − tổng tiền đã trả.
 * Giá hiện tại = giá nhập gần nhất (ngày nào không nhập thì dùng giá lần trước).
 */
@Service
public class GoldService {
    private final CurrentFamilyService currentFamily;
    private final GoldPurchaseRepository purchaseRepository;
    private final GoldPriceRepository priceRepository;

    public GoldService(
            CurrentFamilyService currentFamily,
            GoldPurchaseRepository purchaseRepository,
            GoldPriceRepository priceRepository) {
        this.currentFamily = currentFamily;
        this.purchaseRepository = purchaseRepository;
        this.priceRepository = priceRepository;
    }

    @Transactional(readOnly = true)
    public GoldOverview overview(String username) {
        Family family = currentFamily.findFamily(username);
        GoldPriceView current = priceRepository.findFirstByFamily_IdOrderByPriceDateDescIdDesc(family.getId())
                .map(GoldService::toView)
                .orElse(null);
        BigDecimal price = current == null ? null : current.pricePerChi();

        List<GoldPurchaseView> purchases = purchaseRepository
                .findByFamily_IdOrderByPurchasedOnDescIdDesc(family.getId()).stream()
                .map(purchase -> toView(purchase, price))
                .toList();

        BigDecimal quantity = purchases.stream().map(GoldPurchaseView::quantityChi).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = purchases.stream().map(GoldPurchaseView::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = price == null ? null : money(quantity.multiply(price));
        BigDecimal profit = value == null ? null : value.subtract(cost);
        GoldTotals totals = new GoldTotals(quantity, cost, value, profit, percent(profit, cost));

        List<GoldPriceView> recent = priceRepository.findTop10ByFamily_IdOrderByPriceDateDesc(family.getId()).stream()
                .map(GoldService::toView)
                .toList();
        return new GoldOverview(current, recent, purchases, totals);
    }

    @Transactional
    public GoldOverview addPurchase(String username, GoldPurchaseRequest request) {
        User user = currentFamily.findUser(username);
        Family family = currentFamily.findFamily(user);
        validateDate(request.purchasedOn(), "Ngày mua");
        purchaseRepository.save(new GoldPurchase(
                family, user, request.purchasedOn(), request.quantityChi(), request.totalCost(), normalize(request.note())));
        return overview(username);
    }

    @Transactional
    public GoldOverview updatePurchase(String username, Long id, GoldPurchaseRequest request) {
        Family family = currentFamily.findFamily(username);
        validateDate(request.purchasedOn(), "Ngày mua");
        findPurchase(family, id).update(
                request.purchasedOn(), request.quantityChi(), request.totalCost(), normalize(request.note()));
        return overview(username);
    }

    @Transactional
    public GoldOverview deletePurchase(String username, Long id) {
        Family family = currentFamily.findFamily(username);
        purchaseRepository.delete(findPurchase(family, id));
        return overview(username);
    }

    /** Nhập giá vàng cho một ngày; nhập lại cùng ngày thì ghi đè. */
    @Transactional
    public GoldOverview setPrice(String username, GoldPriceRequest request) {
        User user = currentFamily.findUser(username);
        Family family = currentFamily.findFamily(user);
        validateDate(request.priceDate(), "Ngày của giá vàng");
        priceRepository.findByFamily_IdAndPriceDate(family.getId(), request.priceDate())
                .ifPresentOrElse(
                        existing -> existing.changePrice(request.pricePerChi(), user),
                        () -> priceRepository.save(new GoldPrice(family, user, request.priceDate(), request.pricePerChi())));
        return overview(username);
    }

    // ---- Tính toán ----

    static GoldPurchaseView toView(GoldPurchase purchase, BigDecimal currentPrice) {
        BigDecimal costPerChi = purchase.getTotalCost().divide(purchase.getQuantityChi(), 0, RoundingMode.HALF_UP);
        BigDecimal value = currentPrice == null ? null : money(purchase.getQuantityChi().multiply(currentPrice));
        BigDecimal profit = value == null ? null : value.subtract(purchase.getTotalCost());
        return new GoldPurchaseView(
                purchase.getId(),
                purchase.getPurchasedOn(),
                purchase.getQuantityChi(),
                purchase.getTotalCost(),
                costPerChi,
                purchase.getNote(),
                value,
                profit,
                percent(profit, purchase.getTotalCost()));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    /** Phần trăm lãi/lỗ, 1 chữ số thập phân. */
    private static BigDecimal percent(BigDecimal profit, BigDecimal cost) {
        if (profit == null || cost.signum() == 0) return null;
        return profit.multiply(BigDecimal.valueOf(100)).divide(cost, 1, RoundingMode.HALF_UP);
    }

    private static GoldPriceView toView(GoldPrice price) {
        return new GoldPriceView(price.getPriceDate(), price.getPricePerChi());
    }

    private GoldPurchase findPurchase(Family family, Long id) {
        return purchaseRepository.findByIdAndFamily_Id(id, family.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lần mua vàng này."));
    }

    private static void validateDate(LocalDate date, String label) {
        if (date.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " không được ở tương lai.");
        }
    }

    private static String normalize(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
