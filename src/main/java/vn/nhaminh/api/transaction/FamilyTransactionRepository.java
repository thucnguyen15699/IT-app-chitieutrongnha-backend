package vn.nhaminh.api.transaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyTransactionRepository extends JpaRepository<FamilyTransaction, Long> {
    Optional<FamilyTransaction> findByIdAndFamily_Id(Long id, Long familyId);

    boolean existsByCategory_Id(Long categoryId);

    List<FamilyTransaction> findByFamily_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long familyId, LocalDateTime start, LocalDateTime end);

    /** Dùng cho thống kê: nạp sẵn danh mục và đối tượng để tránh truy vấn N+1. */
    @Query("""
            select t from FamilyTransaction t
            left join fetch t.category
            left join fetch t.target
            where t.family.id = :familyId
              and t.occurredAt >= :start
              and t.occurredAt < :end
            """)
    List<FamilyTransaction> findForStats(
            @Param("familyId") Long familyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
