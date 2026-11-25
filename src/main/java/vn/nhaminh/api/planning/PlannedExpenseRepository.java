package vn.nhaminh.api.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlannedExpenseRepository extends JpaRepository<PlannedExpense, Long> {
    @Query("""
            select p from PlannedExpense p
            left join fetch p.category
            left join fetch p.target
            where p.family.id = :familyId and p.status = :status
            order by p.dueOn, p.id
            """)
    List<PlannedExpense> findByFamilyAndStatus(
            @Param("familyId") Long familyId, @Param("status") PlannedExpense.Status status);

    Optional<PlannedExpense> findByIdAndFamily_Id(Long id, Long familyId);

    @Query("""
            select coalesce(sum(p.amount), 0) from PlannedExpense p
            where p.family.id = :familyId and p.status = :status
            """)
    BigDecimal sumByFamilyAndStatus(
            @Param("familyId") Long familyId, @Param("status") PlannedExpense.Status status);

    long countByFamily_IdAndStatus(Long familyId, PlannedExpense.Status status);

    boolean existsByCategory_Id(Long categoryId);

    /** Khoản chưa chi đã đến hạn (hạn ≤ ngày cho trước), hạn sớm nhất trước. */
    @Query("""
            select p.id from PlannedExpense p
            where p.status = vn.nhaminh.api.planning.PlannedExpense.Status.PENDING and p.dueOn <= :today
            order by p.dueOn, p.id
            """)
    List<Long> findDuePendingIds(@Param("today") java.time.LocalDate today);

    /** Nạp sẵn mọi thứ cần để tự ghi giao dịch (chạy ngoài request, không có phiên lazy). */
    @Query("""
            select p from PlannedExpense p
            join fetch p.family
            join fetch p.category
            join fetch p.createdBy
            left join fetch p.target
            where p.id = :id
            """)
    Optional<PlannedExpense> findForAutoPay(@Param("id") Long id);
}
