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
}
