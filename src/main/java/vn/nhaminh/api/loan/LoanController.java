package vn.nhaminh.api.loan;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.loan.LoanDtos.LoanOverview;
import vn.nhaminh.api.loan.LoanDtos.LoanRequest;
import vn.nhaminh.api.loan.LoanDtos.RepaymentRequest;

/** Mọi thao tác trả về toàn bộ danh sách khoản vay để giao diện cập nhật tổng nợ một lần. */
@RestController
@RequestMapping("/api/loans")
public class LoanController {
    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public LoanOverview overview(Authentication authentication) {
        return loanService.overview(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanOverview create(Authentication authentication, @Valid @RequestBody LoanRequest request) {
        return loanService.create(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public LoanOverview update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody LoanRequest request) {
        return loanService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public LoanOverview delete(Authentication authentication, @PathVariable Long id) {
        return loanService.delete(authentication.getName(), id);
    }

    @PostMapping("/{id}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanOverview repay(Authentication authentication, @PathVariable Long id, @Valid @RequestBody RepaymentRequest request) {
        return loanService.repay(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}/repayments/{repaymentId}")
    public LoanOverview deleteRepayment(Authentication authentication, @PathVariable Long id, @PathVariable Long repaymentId) {
        return loanService.deleteRepayment(authentication.getName(), id, repaymentId);
    }
}
