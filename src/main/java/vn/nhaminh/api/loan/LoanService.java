package vn.nhaminh.api.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.loan.LoanDtos.LoanOverview;
import vn.nhaminh.api.loan.LoanDtos.LoanRequest;
import vn.nhaminh.api.loan.LoanDtos.LoanView;
import vn.nhaminh.api.loan.LoanDtos.RepaymentRequest;
import vn.nhaminh.api.loan.LoanDtos.RepaymentView;
import vn.nhaminh.api.user.User;

/**
 * Khoản vay & trả nợ. Còn nợ mỗi khoản = số tiền vay − tổng các lần đã trả.
 * Tiền vay được tính là tiền vào ví (ngày vay), tiền trả nợ là tiền rời khỏi ví (ngày trả).
 */
@Service
public class LoanService {
    private final CurrentFamilyService currentFamily;
    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository repaymentRepository;

    public LoanService(
            CurrentFamilyService currentFamily,
            LoanRepository loanRepository,
            LoanRepaymentRepository repaymentRepository) {
        this.currentFamily = currentFamily;
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
    }

    @Transactional(readOnly = true)
    public LoanOverview overview(String username) {
        Family family = currentFamily.findFamily(username);
        Map<Long, List<LoanRepayment>> byLoan = new HashMap<>();
        for (LoanRepayment repayment : repaymentRepository.findByFamily(family.getId())) {
            byLoan.computeIfAbsent(repayment.getLoan().getId(), key -> new ArrayList<>()).add(repayment);
        }
        List<LoanView> loans = loanRepository.findByFamily_IdOrderByBorrowedOnDescIdDesc(family.getId()).stream()
                .map(loan -> toView(loan, byLoan.getOrDefault(loan.getId(), List.of())))
                // Khoản còn nợ lên trước, khoản đã trả hết xuống cuối.
                .sorted((left, right) -> Boolean.compare(left.paidOff(), right.paidOff()))
                .toList();
        BigDecimal borrowed = loans.stream().map(LoanView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal repaid = loans.stream().map(LoanView::repaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debt = loans.stream().map(LoanView::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LoanOverview(loans, borrowed, repaid, debt);
    }

    @Transactional
    public LoanOverview create(String username, LoanRequest request) {
        User user = currentFamily.findUser(username);
        Family family = currentFamily.findFamily(user);
        validate(request);
        loanRepository.save(new Loan(
                family, user, request.lenderType(), request.lenderName().trim(), request.amount(), request.borrowedOn(),
                request.repaymentMode(), request.installmentAmount(), request.dueOn(), normalize(request.note())));
        return overview(username);
    }

    @Transactional
    public LoanOverview update(String username, Long id, LoanRequest request) {
        Family family = currentFamily.findFamily(username);
        Loan loan = findLoan(family, id);
        validate(request);
        if (request.amount().compareTo(repaymentRepository.sumByLoan(loan.getId())) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền vay không được nhỏ hơn số đã trả.");
        }
        loan.update(request.lenderType(), request.lenderName().trim(), request.amount(), request.borrowedOn(),
                request.repaymentMode(), request.installmentAmount(), request.dueOn(), normalize(request.note()));
        return overview(username);
    }

    @Transactional
    public LoanOverview delete(String username, Long id) {
        Family family = currentFamily.findFamily(username);
        Loan loan = findLoan(family, id);
        repaymentRepository.deleteByLoanId(loan.getId());
        loanRepository.delete(loan);
        return overview(username);
    }

    /** Trả một phần (hoặc toàn bộ) khoản vay; không được trả quá số còn nợ. */
    @Transactional
    public LoanOverview repay(String username, Long id, RepaymentRequest request) {
        User user = currentFamily.findUser(username);
        Family family = currentFamily.findFamily(user);
        Loan loan = findLoan(family, id);
        if (request.paidOn().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày trả không được ở tương lai.");
        }
        if (request.paidOn().isBefore(loan.getBorrowedOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày trả không được trước ngày vay.");
        }
        BigDecimal remaining = loan.getAmount().subtract(repaymentRepository.sumByLoan(loan.getId()));
        if (remaining.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khoản vay này đã trả hết rồi.");
        }
        if (request.amount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền trả lớn hơn số còn nợ.");
        }
        repaymentRepository.save(new LoanRepayment(loan, user, request.amount(), request.paidOn(), normalize(request.note())));
        return overview(username);
    }

    /** Xóa một lần trả ghi nhầm: số còn nợ tăng lại tương ứng. */
    @Transactional
    public LoanOverview deleteRepayment(String username, Long loanId, Long repaymentId) {
        Family family = currentFamily.findFamily(username);
        Loan loan = findLoan(family, loanId);
        LoanRepayment repayment = repaymentRepository.findByIdAndLoan_Id(repaymentId, loan.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lần trả này."));
        repaymentRepository.delete(repayment);
        return overview(username);
    }

    // ---- Hỗ trợ ----

    private static LoanView toView(Loan loan, List<LoanRepayment> repayments) {
        BigDecimal repaid = repayments.stream().map(LoanRepayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = loan.getAmount().subtract(repaid).max(BigDecimal.ZERO);
        return new LoanView(
                loan.getId(),
                loan.getLenderType(),
                loan.getLenderName(),
                loan.getAmount(),
                loan.getBorrowedOn(),
                loan.getRepaymentMode(),
                loan.getInstallmentAmount(),
                loan.getDueOn(),
                loan.getNote(),
                repaid,
                remaining,
                remaining.signum() == 0,
                repayments.stream()
                        .map(r -> new RepaymentView(r.getId(), r.getAmount(), r.getPaidOn(), r.getNote()))
                        .toList());
    }

    private static void validate(LoanRequest request) {
        if (request.borrowedOn().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày vay không được ở tương lai.");
        }
        if (request.dueOn() != null && request.dueOn().isBefore(request.borrowedOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hạn trả không được trước ngày vay.");
        }
        if (request.repaymentMode() == Loan.RepaymentMode.INSTALLMENT && request.installmentAmount() != null
                && request.installmentAmount().compareTo(request.amount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền trả mỗi lần không được lớn hơn số tiền vay.");
        }
    }

    private Loan findLoan(Family family, Long id) {
        return loanRepository.findByIdAndFamily_Id(id, family.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản vay."));
    }

    private static String normalize(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
