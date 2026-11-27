package vn.nhaminh.api.transaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.transaction.TransactionDtos.CategoryView;
import vn.nhaminh.api.transaction.TransactionDtos.CreateTargetRequest;
import vn.nhaminh.api.transaction.TransactionDtos.StatsView;
import vn.nhaminh.api.transaction.TransactionDtos.TargetView;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionList;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionRequest;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionView;
import vn.nhaminh.api.user.User;

@Service
public class TransactionService {
    private final CurrentFamilyService currentFamily;
    private final ExpenseCategoryRepository categoryRepository;
    private final FamilyTransactionRepository transactionRepository;
    private final SpendingTargetRepository targetRepository;
    private final ApplicationEventPublisher events;

    public TransactionService(
            CurrentFamilyService currentFamily,
            ExpenseCategoryRepository categoryRepository,
            FamilyTransactionRepository transactionRepository,
            SpendingTargetRepository targetRepository,
            ApplicationEventPublisher events) {
        this.currentFamily = currentFamily;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.targetRepository = targetRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TargetView> targets(String username) {
        Family family = findFamily(username);
        return targetRepository.findAvailableTargets(family.getId()).stream()
                .map(TransactionService::toTargetView)
                .toList();
    }

    @Transactional
    public TargetView createTarget(String username, CreateTargetRequest request) {
        Family family = findFamily(username);
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên đối tượng.");
        }
        if (targetRepository.existsAvailableName(family.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đối tượng này đã có trong danh sách.");
        }
        return toTargetView(targetRepository.save(new SpendingTarget(family, name, 100)));
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories(String username, TransactionType type) {
        Family family = findFamily(username);
        return categoryRepository.findAvailableCategories(family.getId(), type).stream()
                .map(CategoryService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionList listMonth(String username, String requestedMonth) {
        YearMonth month;
        try {
            month = requestedMonth == null ? YearMonth.now() : YearMonth.parse(requestedMonth);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng cần có định dạng YYYY-MM.");
        }

        Family family = findFamily(username);
        List<TransactionView> transactions = transactionRepository
                .findByFamily_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                        family.getId(),
                        month.atDay(1).atStartOfDay(),
                        month.plusMonths(1).atDay(1).atStartOfDay())
                .stream()
                .map(this::toView)
                .toList();
        return new TransactionList(transactions);
    }

    /** Thống kê theo tháng (month = YYYY-MM) hoặc theo năm (year = YYYY). Không truyền gì = tháng hiện tại. */
    @Transactional(readOnly = true)
    public StatsView stats(String username, String requestedMonth, Integer requestedYear) {
        Family family = findFamily(username);
        if (requestedYear != null) {
            if (requestedYear < 2000 || requestedYear > Year.now().getValue()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Năm thống kê không hợp lệ.");
            }
            Year year = Year.of(requestedYear);
            var transactions = transactionRepository.findForStats(
                    family.getId(),
                    year.atDay(1).atStartOfDay(),
                    year.plusYears(1).atDay(1).atStartOfDay());
            return TransactionStats.build(year.toString(), transactions, true);
        }

        YearMonth month;
        try {
            month = requestedMonth == null ? YearMonth.now() : YearMonth.parse(requestedMonth);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng cần có định dạng YYYY-MM.");
        }
        var transactions = transactionRepository.findForStats(
                family.getId(),
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
        return TransactionStats.build(month.toString(), transactions, false);
    }

    @Transactional
    public TransactionView create(String username, TransactionRequest request) {
        validateAndNormalizeRequest(request); // báo lỗi dữ liệu trước khi tra tài khoản
        User user = findUser(username);
        return createInFamily(user, findFamily(user), request, false);
    }

    /**
     * Tạo giao dịch cho một gia đình cụ thể.
     * automatic = true khi hệ thống tự ghi (khoản sắp chi đến hạn): thông báo sẽ ghi rõ là tự động.
     */
    @Transactional
    public TransactionView createInFamily(User user, Family family, TransactionRequest request, boolean automatic) {
        String note = validateAndNormalizeRequest(request);
        ExpenseCategory category = findCategory(family, request);
        SpendingTarget target = findTarget(family, request);
        FamilyTransaction transaction = transactionRepository.save(new FamilyTransaction(
                family,
                category,
                target,
                user,
                request.type(),
                request.amount(),
                note,
                request.occurredOn().atStartOfDay()));
        TransactionView view = toView(transaction);
        // Báo cho các thành viên khác (gửi sau khi lưu xong, xem TransactionNotifier).
        events.publishEvent(new TransactionCreatedEvent(
                view.id(), family.getId(), user.getId(), user.getName(), view.type(), view.amount(),
                view.categoryName(), view.targetName(), view.note(), automatic, view.occurredOn()));
        return view;
    }

    @Transactional
    public TransactionView update(String username, Long transactionId, TransactionRequest request) {
        Family family = findFamily(username);
        FamilyTransaction transaction = transactionRepository.findByIdAndFamily_Id(transactionId, family.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch trong gia đình."));
        String note = validateAndNormalizeRequest(request);
        ExpenseCategory category = findCategory(family, request);
        SpendingTarget target = findTarget(family, request);
        transaction.update(
                category,
                target,
                request.type(),
                request.amount(),
                note,
                request.occurredOn().atStartOfDay());
        return toView(transactionRepository.save(transaction));
    }

    /** Xóa giao dịch nhập sai. Chỉ thành viên trong gia đình (admin đang "chỉ xem" bị chặn ở findFamily). */
    @Transactional
    public void delete(String username, Long transactionId) {
        Family family = findFamily(username);
        FamilyTransaction transaction = transactionRepository.findByIdAndFamily_Id(transactionId, family.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch trong gia đình."));
        transactionRepository.detachFromPlannedExpenses(transaction.getId());
        transactionRepository.deleteById(transaction.getId());
    }

    private User findUser(String username) {
        return currentFamily.findUser(username);
    }

    private Family findFamily(String username) {
        return currentFamily.findFamily(username);
    }

    private Family findFamily(User user) {
        return currentFamily.findFamily(user);
    }

    private ExpenseCategory findCategory(Family family, TransactionRequest request) {
        ExpenseCategory category = categoryRepository.findWithFamilyById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục không tồn tại."));
        Long categoryFamilyId = category.getFamily() == null ? null : category.getFamily().getId();
        if (categoryFamilyId != null && !categoryFamilyId.equals(family.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Danh mục này không thuộc gia đình của bạn.");
        }
        if (category.getType() != request.type()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại giao dịch không khớp với danh mục.");
        }
        return category;
    }

    /** Khoản thu không gắn đối tượng; khoản chi không chọn đối tượng thì tính là cả nhà. */
    private SpendingTarget findTarget(Family family, TransactionRequest request) {
        if (request.type() != TransactionType.EXPENSE || request.targetId() == null) {
            return null;
        }
        SpendingTarget target = targetRepository.findWithFamilyById(request.targetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đối tượng chi tiêu không tồn tại."));
        Long targetFamilyId = target.getFamily() == null ? null : target.getFamily().getId();
        if (targetFamilyId != null && !targetFamilyId.equals(family.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đối tượng này không thuộc gia đình của bạn.");
        }
        return target;
    }

    private static TargetView toTargetView(SpendingTarget target) {
        return new TargetView(target.getId(), target.getName(), target.getFamily() != null);
    }

    private static String validateAndNormalizeRequest(TransactionRequest request) {
        if (request.occurredOn().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể ghi giao dịch từ hôm nay trở về trước.");
        }
        String note = normalizeOptional(request.note());
        if (request.type() == TransactionType.EXPENSE && note == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng ghi chú khoản chi đã mua hoặc thanh toán.");
        }
        return note;
    }

    private TransactionView toView(FamilyTransaction transaction) {
        ExpenseCategory category = transaction.getCategory();
        SpendingTarget target = transaction.getTarget();
        String targetName = target != null
                ? target.getName()
                : transaction.getType() == TransactionType.EXPENSE ? "Cả nhà" : null;
        return new TransactionView(
                transaction.getId(),
                transaction.getType(),
                category == null ? null : category.getId(),
                category == null ? "Khác" : category.getName(),
                target == null ? null : target.getId(),
                targetName,
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getOccurredAt().toLocalDate());
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
