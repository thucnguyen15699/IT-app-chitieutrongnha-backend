package vn.nhaminh.api.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.planning.PlanningDtos.ContributionRequest;
import vn.nhaminh.api.planning.PlanningDtos.ContributionView;
import vn.nhaminh.api.planning.PlanningDtos.PlannedExpenseRequest;
import vn.nhaminh.api.planning.PlanningDtos.PlannedExpenseView;
import vn.nhaminh.api.planning.PlanningDtos.PlanningSummary;
import vn.nhaminh.api.planning.PlanningDtos.SavingsGoalRequest;
import vn.nhaminh.api.planning.PlanningDtos.SavingsGoalView;
import vn.nhaminh.api.transaction.ExpenseCategory;
import vn.nhaminh.api.transaction.ExpenseCategoryRepository;
import vn.nhaminh.api.transaction.FamilyTransactionRepository;
import vn.nhaminh.api.transaction.SpendingTarget;
import vn.nhaminh.api.transaction.SpendingTargetRepository;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionRequest;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionView;
import vn.nhaminh.api.transaction.TransactionService;
import vn.nhaminh.api.transaction.TransactionType;
import vn.nhaminh.api.user.User;

@Service
public class PlanningService {
    private final CurrentFamilyService currentFamily;
    private final PlannedExpenseRepository plannedRepository;
    private final SavingsGoalRepository goalRepository;
    private final SavingsContributionRepository contributionRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final SpendingTargetRepository targetRepository;
    private final FamilyTransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final GoldPurchaseRepository goldPurchaseRepository;

    public PlanningService(
            CurrentFamilyService currentFamily,
            PlannedExpenseRepository plannedRepository,
            SavingsGoalRepository goalRepository,
            SavingsContributionRepository contributionRepository,
            ExpenseCategoryRepository categoryRepository,
            SpendingTargetRepository targetRepository,
            FamilyTransactionRepository transactionRepository,
            TransactionService transactionService,
            GoldPurchaseRepository goldPurchaseRepository) {
        this.currentFamily = currentFamily;
        this.plannedRepository = plannedRepository;
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.categoryRepository = categoryRepository;
        this.targetRepository = targetRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.goldPurchaseRepository = goldPurchaseRepository;
    }

    // ================= Khoản sắp phải chi =================

    @Transactional(readOnly = true)
    public List<PlannedExpenseView> listPlanned(String username) {
        Family family = currentFamily.findFamily(username);
        return plannedRepository.findByFamilyAndStatus(family.getId(), PlannedExpense.Status.PENDING).stream()
                .map(PlanningService::toView)
                .toList();
    }

    @Transactional
    public PlannedExpenseView createPlanned(String username, PlannedExpenseRequest request) {
        User user = currentFamily.findUser(username);
        Family family = currentFamily.findFamily(user);
        PlannedExpense planned = plannedRepository.save(new PlannedExpense(
                family,
                findExpenseCategory(family, request.categoryId()),
                findTarget(family, request.targetId()),
                user,
                request.amount(),
                request.note().trim(),
                request.dueOn()));
        return toView(planned);
    }

    @Transactional
    public PlannedExpenseView updatePlanned(String username, Long id, PlannedExpenseRequest request) {
        Family family = currentFamily.findFamily(username);
        PlannedExpense planned = findPendingPlanned(family, id);
        planned.update(
                findExpenseCategory(family, request.categoryId()),
                findTarget(family, request.targetId()),
                request.amount(),
                request.note().trim(),
                request.dueOn());
        return toView(planned);
    }

    @Transactional
    public void deletePlanned(String username, Long id) {
        Family family = currentFamily.findFamily(username);
        plannedRepository.delete(findPendingPlanned(family, id));
    }

    /**
     * Tự ghi MỘT khoản sắp chi đã đến hạn thành giao dịch chi (ngày giao dịch = ngày hạn).
     * Giao dịch thuộc gia đình của khoản sắp chi, người ghi là người đã lập khoản đó.
     * Chạy trong giao dịch DB riêng để khoản này lỗi không ảnh hưởng khoản khác.
     *
     * @return true nếu đã ghi; false nếu khoản không còn chờ hoặc chưa đến hạn (đã có người xử lý).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean autoPayDue(Long plannedId, LocalDate today) {
        PlannedExpense planned = plannedRepository.findForAutoPay(plannedId).orElse(null);
        if (planned == null || planned.getStatus() != PlannedExpense.Status.PENDING || planned.getDueOn().isAfter(today)) {
            return false;
        }
        TransactionView transaction = transactionService.createInFamily(
                planned.getCreatedBy(),
                planned.getFamily(),
                new TransactionRequest(
                        TransactionType.EXPENSE,
                        planned.getCategory().getId(),
                        planned.getAmount(),
                        planned.getDescription(),
                        planned.getDueOn(),
                        planned.getTarget() == null ? null : planned.getTarget().getId()),
                true);
        planned.markPaid(transactionRepository.getReferenceById(transaction.id()));
        return true;
    }

    /** Bấm "Đã chi": tạo giao dịch chi thật vào hôm nay và đánh dấu khoản dự kiến là đã chi. */
    @Transactional
    public TransactionView payPlanned(String username, Long id) {
        Family family = currentFamily.findFamily(username);
        PlannedExpense planned = findPendingPlanned(family, id);
        TransactionView transaction = transactionService.create(username, new TransactionRequest(
                TransactionType.EXPENSE,
                planned.getCategory().getId(),
                planned.getAmount(),
                planned.getDescription(),
                LocalDate.now(),
                planned.getTarget() == null ? null : planned.getTarget().getId()));
        planned.markPaid(transactionRepository.getReferenceById(transaction.id()));
        return transaction;
    }

    // ================= Tiết kiệm =================

    @Transactional(readOnly = true)
    public List<SavingsGoalView> listGoals(String username) {
        Family family = currentFamily.findFamily(username);
        Map<Long, BigDecimal> balances = balancesByGoal(family.getId());
        return goalRepository.findByFamily_IdOrderByCreatedAtAsc(family.getId()).stream()
                .map(goal -> toView(goal, balances.getOrDefault(goal.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public SavingsGoalView createGoal(String username, SavingsGoalRequest request) {
        Family family = currentFamily.findFamily(username);
        SavingsGoal goal = goalRepository.save(new SavingsGoal(
                family, request.name().trim(), request.purpose().trim(), request.targetAmount(), request.deadline()));
        return toView(goal, BigDecimal.ZERO);
    }

    @Transactional
    public SavingsGoalView updateGoal(String username, Long id, SavingsGoalRequest request) {
        Family family = currentFamily.findFamily(username);
        SavingsGoal goal = findGoal(family, id);
        goal.update(request.name().trim(), request.purpose().trim(), request.targetAmount(), request.deadline());
        return toView(goal, balanceOf(goal.getId()));
    }

    @Transactional
    public void deleteGoal(String username, Long id) {
        Family family = currentFamily.findFamily(username);
        SavingsGoal goal = findGoal(family, id);
        contributionRepository.deleteByGoalId(goal.getId());
        goalRepository.delete(goal);
    }

    @Transactional
    public SavingsGoalView addContribution(String username, Long goalId, ContributionRequest request) {
        User user = currentFamily.findUser(username);
        Family family = currentFamily.findFamily(user);
        SavingsGoal goal = findGoal(family, goalId);
        if (request.occurredOn().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ ghi được tiền gửi/rút từ hôm nay trở về trước.");
        }
        BigDecimal balance = balanceOf(goal.getId());
        if (request.type() == SavingsContribution.Type.WITHDRAW && request.amount().compareTo(balance) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền rút lớn hơn số đã tiết kiệm.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        contributionRepository.save(new SavingsContribution(
                goal, user, request.type(), request.amount(), request.occurredOn(), note));
        return toView(goal, balanceOf(goal.getId()));
    }

    // ================= Tổng hợp =================

    @Transactional(readOnly = true)
    public PlanningSummary summary(String username, String requestedMonth) {
        YearMonth month;
        try {
            month = requestedMonth == null ? YearMonth.now() : YearMonth.parse(requestedMonth);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng cần có định dạng YYYY-MM.");
        }
        Family family = currentFamily.findFamily(username);
        Long familyId = family.getId();
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        BigDecimal deposited = contributionRepository.sumByFamilyTypeAndPeriod(
                familyId, SavingsContribution.Type.DEPOSIT, start, end);
        BigDecimal withdrawn = contributionRepository.sumByFamilyTypeAndPeriod(
                familyId, SavingsContribution.Type.WITHDRAW, start, end);
        BigDecimal totalSaved = balancesByGoal(familyId).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PlanningSummary(
                plannedRepository.sumByFamilyAndStatus(familyId, PlannedExpense.Status.PENDING),
                plannedRepository.countByFamily_IdAndStatus(familyId, PlannedExpense.Status.PENDING),
                deposited.subtract(withdrawn),
                totalSaved,
                goldPurchaseRepository.sumCostInPeriod(familyId, start, end));
    }

    // ================= Hỗ trợ =================

    private PlannedExpense findPendingPlanned(Family family, Long id) {
        PlannedExpense planned = plannedRepository.findByIdAndFamily_Id(id, family.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản sắp chi."));
        if (planned.getStatus() != PlannedExpense.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khoản này đã được chi rồi.");
        }
        return planned;
    }

    private SavingsGoal findGoal(Family family, Long id) {
        return goalRepository.findByIdAndFamily_Id(id, family.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiết kiệm."));
    }

    private ExpenseCategory findExpenseCategory(Family family, Long categoryId) {
        ExpenseCategory category = categoryRepository.findWithFamilyById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục không tồn tại."));
        Long ownerId = category.getFamily() == null ? null : category.getFamily().getId();
        if (ownerId != null && !ownerId.equals(family.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Danh mục này không thuộc gia đình của bạn.");
        }
        if (category.getType() != TransactionType.EXPENSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản sắp chi cần chọn danh mục chi.");
        }
        return category;
    }

    private SpendingTarget findTarget(Family family, Long targetId) {
        if (targetId == null) return null;
        SpendingTarget target = targetRepository.findWithFamilyById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đối tượng chi tiêu không tồn tại."));
        Long ownerId = target.getFamily() == null ? null : target.getFamily().getId();
        if (ownerId != null && !ownerId.equals(family.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đối tượng này không thuộc gia đình của bạn.");
        }
        return target;
    }

    private BigDecimal balanceOf(Long goalId) {
        return contributionRepository.sumByGoalAndType(goalId, SavingsContribution.Type.DEPOSIT)
                .subtract(contributionRepository.sumByGoalAndType(goalId, SavingsContribution.Type.WITHDRAW));
    }

    private Map<Long, BigDecimal> balancesByGoal(Long familyId) {
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (Object[] row : contributionRepository.sumByGoalAndType(familyId)) {
            Long goalId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[2];
            BigDecimal signed = row[1] == SavingsContribution.Type.WITHDRAW ? sum.negate() : sum;
            balances.merge(goalId, signed, BigDecimal::add);
        }
        return balances;
    }

    private static PlannedExpenseView toView(PlannedExpense planned) {
        SpendingTarget target = planned.getTarget();
        return new PlannedExpenseView(
                planned.getId(),
                planned.getCategory().getId(),
                planned.getCategory().getName(),
                target == null ? null : target.getId(),
                target == null ? "Cả nhà" : target.getName(),
                planned.getAmount(),
                planned.getDescription(),
                planned.getDueOn(),
                planned.getDueOn().isBefore(LocalDate.now()));
    }

    private SavingsGoalView toView(SavingsGoal goal, BigDecimal saved) {
        List<ContributionView> recent = goal.getId() == null ? List.of()
                : contributionRepository.findTop5ByGoal_IdOrderByOccurredOnDescIdDesc(goal.getId()).stream()
                        .map(c -> new ContributionView(c.getId(), c.getType(), c.getAmount(), c.getOccurredOn(), c.getNote()))
                        .toList();
        return new SavingsGoalView(
                goal.getId(), goal.getName(), goal.getPurpose(), goal.getTargetAmount(), saved, goal.getDeadline(), recent);
    }
}
