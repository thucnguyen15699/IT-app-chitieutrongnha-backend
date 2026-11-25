package vn.nhaminh.api.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.planning.PlannedExpenseRepository;
import vn.nhaminh.api.transaction.TransactionDtos.CategoryView;
import vn.nhaminh.api.transaction.TransactionDtos.CreateCategoryRequest;

/** Gia đình tự tạo/xóa danh mục thu chi của riêng mình (danh mục mặc định thì không xóa được). */
@Service
public class CategoryService {
    private final CurrentFamilyService currentFamily;
    private final ExpenseCategoryRepository categoryRepository;
    private final FamilyTransactionRepository transactionRepository;
    private final PlannedExpenseRepository plannedRepository;

    public CategoryService(
            CurrentFamilyService currentFamily,
            ExpenseCategoryRepository categoryRepository,
            FamilyTransactionRepository transactionRepository,
            PlannedExpenseRepository plannedRepository) {
        this.currentFamily = currentFamily;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.plannedRepository = plannedRepository;
    }

    @Transactional
    public CategoryView create(String username, CreateCategoryRequest request) {
        Family family = currentFamily.findFamily(username);
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên danh mục.");
        }
        if (categoryRepository.existsAvailableName(family.getId(), name, request.type())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Danh mục này đã có rồi.");
        }
        return toView(categoryRepository.save(new ExpenseCategory(family, name, request.type())));
    }

    @Transactional
    public void delete(String username, Long id) {
        Family family = currentFamily.findFamily(username);
        ExpenseCategory category = categoryRepository.findWithFamilyById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục."));
        if (category.getFamily() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không thể xóa danh mục mặc định.");
        }
        if (!category.getFamily().getId().equals(family.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Danh mục này không thuộc gia đình của bạn.");
        }
        if (transactionRepository.existsByCategory_Id(id) || plannedRepository.existsByCategory_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Danh mục đã có giao dịch hoặc khoản sắp chi nên không xóa được.");
        }
        categoryRepository.delete(category);
    }

    static CategoryView toView(ExpenseCategory category) {
        return new CategoryView(category.getId(), category.getName(), category.getType(), category.getFamily() != null);
    }
}
