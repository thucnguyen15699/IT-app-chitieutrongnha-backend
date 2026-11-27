package vn.nhaminh.api.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsContributionRepository extends JpaRepository<SavingsContribution, Long> {
    /** Tổng tiền theo từng mục và từng loại (gửi/rút): mỗi dòng là [goalId, type, sum]. */
    @Query("""
            select c.goal.id, c.type, sum(c.amount) from SavingsContribution c
            where c.goal.family.id = :familyId
            group by c.goal.id, c.type
            """)
    List<Object[]> sumByGoalAndType(@Param("familyId") Long familyId);

    @Query("""
            select coalesce(sum(c.amount), 0) from SavingsContribution c
            where c.goal.id = :goalId and c.type = :type
            """)
    BigDecimal sumByGoalAndType(@Param("goalId") Long goalId, @Param("type") SavingsContribution.Type type);

    /** Tổng tiền gửi (hoặc rút) của cả gia đình trong khoảng ngày [start, end). */
    @Query("""
            select coalesce(sum(c.amount), 0) from SavingsContribution c
            where c.goal.family.id = :familyId and c.type = :type
              and c.occurredOn >= :start and c.occurredOn < :end
            """)
    BigDecimal sumByFamilyTypeAndPeriod(
            @Param("familyId") Long familyId,
            @Param("type") SavingsContribution.Type type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /** Tổng tiền gửi (hoặc rút) của cả gia đình trước ngày :before. */
    @Query("""
            select coalesce(sum(c.amount), 0) from SavingsContribution c
            where c.goal.family.id = :familyId and c.type = :type and c.occurredOn < :before
            """)
    BigDecimal sumByFamilyTypeBefore(
            @Param("familyId") Long familyId,
            @Param("type") SavingsContribution.Type type,
            @Param("before") LocalDate before);

    List<SavingsContribution> findTop5ByGoal_IdOrderByOccurredOnDescIdDesc(Long goalId);

    @Modifying
    @Query("delete from SavingsContribution c where c.goal.id = :goalId")
    void deleteByGoalId(@Param("goalId") Long goalId);
}
