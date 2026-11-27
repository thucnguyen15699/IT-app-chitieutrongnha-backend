package vn.nhaminh.api.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoldPurchaseRepository extends JpaRepository<GoldPurchase, Long> {
    List<GoldPurchase> findByFamily_IdOrderByPurchasedOnDescIdDesc(Long familyId);

    Optional<GoldPurchase> findByIdAndFamily_Id(Long id, Long familyId);

    /** Tổng tiền mua vàng trong khoảng ngày [start, end): tiền đã rời khỏi ví. */
    @Query("""
            select coalesce(sum(g.totalCost), 0) from GoldPurchase g
            where g.family.id = :familyId and g.purchasedOn >= :start and g.purchasedOn < :end
            """)
    BigDecimal sumCostInPeriod(
            @Param("familyId") Long familyId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Tổng tiền mua vàng trước ngày :before. */
    @Query("""
            select coalesce(sum(g.totalCost), 0) from GoldPurchase g
            where g.family.id = :familyId and g.purchasedOn < :before
            """)
    BigDecimal sumCostBefore(@Param("familyId") Long familyId, @Param("before") LocalDate before);
}
