package vn.nhaminh.api.planning;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GoldDtos {
    private GoldDtos() {}

    public record GoldPurchaseRequest(
            @NotNull LocalDate purchasedOn,
            @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal quantityChi,
            @NotNull @DecimalMin("1") @Digits(integer = 13, fraction = 2) BigDecimal totalCost,
            @Size(max = 255) String note) {}

    public record GoldPriceRequest(
            @NotNull LocalDate priceDate,
            @NotNull @DecimalMin("1") @Digits(integer = 13, fraction = 2) BigDecimal pricePerChi) {}

    public record GoldPriceView(LocalDate priceDate, BigDecimal pricePerChi) {}

    /**
     * Một lần mua kèm lãi/lỗ theo giá hiện tại.
     * currentValue/profit/profitPercent = null khi chưa nhập giá vàng lần nào.
     */
    public record GoldPurchaseView(
            Long id,
            LocalDate purchasedOn,
            BigDecimal quantityChi,
            BigDecimal totalCost,
            BigDecimal costPerChi,
            String note,
            BigDecimal currentValue,
            BigDecimal profit,
            BigDecimal profitPercent) {}

    public record GoldTotals(
            BigDecimal quantityChi,
            BigDecimal totalCost,
            BigDecimal currentValue,
            BigDecimal profit,
            BigDecimal profitPercent) {}

    /** currentPrice: giá mới nhất đã nhập (null nếu chưa nhập lần nào). */
    public record GoldOverview(
            GoldPriceView currentPrice,
            List<GoldPriceView> recentPrices,
            List<GoldPurchaseView> purchases,
            GoldTotals totals) {}
}
