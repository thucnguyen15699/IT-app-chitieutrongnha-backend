package vn.nhaminh.api.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoldPriceRepository extends JpaRepository<GoldPrice, Long> {
    /** Giá mới nhất (theo ngày) — dùng làm "giá hiện tại" khi hôm nay chưa nhập. */
    Optional<GoldPrice> findFirstByFamily_IdOrderByPriceDateDescIdDesc(Long familyId);

    Optional<GoldPrice> findByFamily_IdAndPriceDate(Long familyId, LocalDate priceDate);

    List<GoldPrice> findTop10ByFamily_IdOrderByPriceDateDesc(Long familyId);
}
