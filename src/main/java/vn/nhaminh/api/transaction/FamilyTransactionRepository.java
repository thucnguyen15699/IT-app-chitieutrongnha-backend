package vn.nhaminh.api.transaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyTransactionRepository extends JpaRepository<FamilyTransaction, Long> {
    Optional<FamilyTransaction> findByIdAndFamily_Id(Long id, Long familyId);

    /**
     * Trước khi xóa giao dịch: bỏ liên kết từ khoản sắp chi đã "Đã chi" bằng giao dịch này.
     * Khoản sắp chi vẫn giữ trạng thái đã chi (nếu quay về "chưa chi" thì hệ thống sẽ tự ghi lại).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PlannedExpense p set p.paidTransaction = null where p.paidTransaction.id = :transactionId")
    int detachFromPlannedExpenses(@Param("transactionId") Long transactionId);

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
