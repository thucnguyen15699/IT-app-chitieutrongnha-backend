package vn.nhaminh.api.transaction;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.transaction.TransactionDtos.CategoryView;
import vn.nhaminh.api.transaction.TransactionDtos.CreateCategoryRequest;
import vn.nhaminh.api.transaction.TransactionDtos.CreateTargetRequest;
import vn.nhaminh.api.transaction.TransactionDtos.StatsView;
import vn.nhaminh.api.transaction.TransactionDtos.TargetView;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionRequest;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionList;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionView;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;
    private final CategoryService categoryService;

    public TransactionController(TransactionService transactionService, CategoryService categoryService) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(
            Authentication authentication, @Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.create(authentication.getName(), request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(Authentication authentication, @PathVariable Long id) {
        categoryService.delete(authentication.getName(), id);
    }

    @GetMapping("/categories")
    public List<CategoryView> categories(
            Authentication authentication, @RequestParam TransactionType type) {
        return transactionService.categories(authentication.getName(), type);
    }

    @GetMapping("/targets")
    public List<TargetView> targets(Authentication authentication) {
        return transactionService.targets(authentication.getName());
    }

    @PostMapping("/targets")
    @ResponseStatus(HttpStatus.CREATED)
    public TargetView createTarget(
            Authentication authentication, @Valid @RequestBody CreateTargetRequest request) {
        return transactionService.createTarget(authentication.getName(), request);
    }

    @GetMapping("/stats")
    public StatsView stats(
            Authentication authentication,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        return transactionService.stats(authentication.getName(), month, year);
    }

    @GetMapping
    public TransactionList listMonth(
            Authentication authentication, @RequestParam(required = false) String month) {
        return transactionService.listMonth(authentication.getName(), month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView create(
            Authentication authentication, @Valid @RequestBody TransactionRequest request) {
        return transactionService.create(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public TransactionView update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(authentication.getName(), id, request);
    }
}
