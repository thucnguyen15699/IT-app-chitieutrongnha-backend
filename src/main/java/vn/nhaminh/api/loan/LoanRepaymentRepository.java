package vn.nhaminh.api.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    @Query("""
            select r from LoanRepayment r
            where r.loan.family.id = :familyId
            order by r.paidOn desc, r.id desc
            """)
    List<LoanRepayment> findByFamily(@Param("familyId") Long familyId);

    Optional<LoanRepayment> findByIdAndLoan_Id(Long id, Long loanId);

    @Query("select coalesce(sum(r.amount), 0) from LoanRepayment r where r.loan.id = :loanId")
    BigDecimal sumByLoan(@Param("loanId") Long loanId);

    /** Tổng tiền trả nợ trong khoảng ngày [start, end): tiền rời khỏi ví. */
    @Query("""
            select coalesce(sum(r.amount), 0) from LoanRepayment r
            where r.loan.family.id = :familyId and r.paidOn >= :start and r.paidOn < :end
            """)
    BigDecimal sumRepaidInPeriod(
            @Param("familyId") Long familyId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select coalesce(sum(r.amount), 0) from LoanRepayment r
            where r.loan.family.id = :familyId and r.paidOn < :before
            """)
    BigDecimal sumRepaidBefore(@Param("familyId") Long familyId, @Param("before") LocalDate before);

    @Query("select coalesce(sum(r.amount), 0) from LoanRepayment r where r.loan.family.id = :familyId")
    BigDecimal sumRepaid(@Param("familyId") Long familyId);

    @Modifying
    @Query("delete from LoanRepayment r where r.loan.id = :loanId")
    void deleteByLoanId(@Param("loanId") Long loanId);
}
