package vn.nhaminh.api.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByFamily_IdOrderByBorrowedOnDescIdDesc(Long familyId);

    Optional<Loan> findByIdAndFamily_Id(Long id, Long familyId);

    /** Tổng tiền đã vay trong khoảng ngày [start, end): tiền vào ví. */
    @Query("""
            select coalesce(sum(l.amount), 0) from Loan l
            where l.family.id = :familyId and l.borrowedOn >= :start and l.borrowedOn < :end
            """)
    BigDecimal sumBorrowedInPeriod(
            @Param("familyId") Long familyId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select coalesce(sum(l.amount), 0) from Loan l
            where l.family.id = :familyId and l.borrowedOn < :before
            """)
    BigDecimal sumBorrowedBefore(@Param("familyId") Long familyId, @Param("before") LocalDate before);

    @Query("select coalesce(sum(l.amount), 0) from Loan l where l.family.id = :familyId")
    BigDecimal sumBorrowed(@Param("familyId") Long familyId);
}
