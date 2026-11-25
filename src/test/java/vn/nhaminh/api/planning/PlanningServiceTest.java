package vn.nhaminh.api.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.planning.PlanningDtos.ContributionRequest;
import vn.nhaminh.api.planning.PlanningDtos.PlannedExpenseRequest;
import vn.nhaminh.api.transaction.ExpenseCategory;
import vn.nhaminh.api.transaction.ExpenseCategoryRepository;
import vn.nhaminh.api.transaction.FamilyTransaction;
import vn.nhaminh.api.transaction.FamilyTransactionRepository;
import vn.nhaminh.api.transaction.SpendingTargetRepository;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionRequest;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionView;
import vn.nhaminh.api.transaction.TransactionService;
import vn.nhaminh.api.transaction.TransactionType;
import vn.nhaminh.api.user.User;

@ExtendWith(MockitoExtension.class)
class PlanningServiceTest {
    @Mock private CurrentFamilyService currentFamily;
    @Mock private PlannedExpenseRepository plannedRepository;
    @Mock private SavingsGoalRepository goalRepository;
    @Mock private SavingsContributionRepository contributionRepository;
    @Mock private ExpenseCategoryRepository categoryRepository;
    @Mock private SpendingTargetRepository targetRepository;
    @Mock private FamilyTransactionRepository transactionRepository;
    @Mock private TransactionService transactionService;
    @Mock private GoldPurchaseRepository goldPurchaseRepository;
    @InjectMocks private PlanningService planningService;

    private final User user = new User("member", "Member", null, null, "encoded", true);
    private final Family family = new Family("Nhà mình");

    @Test
    void rejectsIncomeCategoryForPlannedExpense() {
        when(currentFamily.findUser("member")).thenReturn(user);
        when(currentFamily.findFamily(user)).thenReturn(family);
        when(categoryRepository.findWithFamilyById(1L))
                .thenReturn(Optional.of(new ExpenseCategory(null, "Lương", TransactionType.INCOME)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> planningService.createPlanned("member", new PlannedExpenseRequest(
                        1L, null, new BigDecimal("5000000"), "Học phí", LocalDate.now().plusMonths(1))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void payingPlannedExpenseCreatesTodayTransactionAndMarksPaid() {
        ExpenseCategory education = org.mockito.Mockito.mock(ExpenseCategory.class);
        when(education.getId()).thenReturn(6L);
        PlannedExpense planned = new PlannedExpense(
                family, education, null, user, new BigDecimal("5000000"), "Học phí kỳ 2", LocalDate.now().plusDays(10));
        when(currentFamily.findFamily("member")).thenReturn(family);
        when(plannedRepository.findByIdAndFamily_Id(3L, null)).thenReturn(Optional.of(planned));
        TransactionView created = new TransactionView(
                99L, TransactionType.EXPENSE, 6L, "Giáo dục", null, "Cả nhà",
                new BigDecimal("5000000"), "Học phí kỳ 2", LocalDate.now());
        when(transactionService.create(eq("member"), any(TransactionRequest.class))).thenReturn(created);
        when(transactionRepository.getReferenceById(99L)).thenReturn(org.mockito.Mockito.mock(FamilyTransaction.class));

        TransactionView result = planningService.payPlanned("member", 3L);

        assertEquals(99L, result.id());
        assertEquals(PlannedExpense.Status.PAID, planned.getStatus());
        verify(transactionService).create(eq("member"), eq(new TransactionRequest(
                TransactionType.EXPENSE, 6L, new BigDecimal("5000000"), "Học phí kỳ 2", LocalDate.now(), null)));

        // Chi lần thứ hai thì bị chặn.
        ResponseStatusException again = assertThrows(ResponseStatusException.class,
                () -> planningService.payPlanned("member", 3L));
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
    }

    @Test
    void cannotWithdrawMoreThanSaved() {
        SavingsGoal goal = org.mockito.Mockito.mock(SavingsGoal.class);
        when(goal.getId()).thenReturn(5L);
        when(currentFamily.findUser("member")).thenReturn(user);
        when(currentFamily.findFamily(user)).thenReturn(family);
        when(goalRepository.findByIdAndFamily_Id(5L, null)).thenReturn(Optional.of(goal));
        when(contributionRepository.sumByGoalAndType(5L, SavingsContribution.Type.DEPOSIT)).thenReturn(new BigDecimal("1000000"));
        when(contributionRepository.sumByGoalAndType(5L, SavingsContribution.Type.WITHDRAW)).thenReturn(BigDecimal.ZERO);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> planningService.addContribution("member", 5L, new ContributionRequest(
                        SavingsContribution.Type.WITHDRAW, new BigDecimal("2000000"), LocalDate.now(), null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(contributionRepository, never()).save(any());
    }

    @Test
    void summaryNetsDepositsAgainstWithdrawalsInMonth() {
        when(currentFamily.findFamily("member")).thenReturn(family);
        when(contributionRepository.sumByFamilyTypeAndPeriod(eq(null), eq(SavingsContribution.Type.DEPOSIT), any(), any()))
                .thenReturn(new BigDecimal("3000000"));
        when(contributionRepository.sumByFamilyTypeAndPeriod(eq(null), eq(SavingsContribution.Type.WITHDRAW), any(), any()))
                .thenReturn(new BigDecimal("500000"));
        when(contributionRepository.sumByGoalAndType((Long) null)).thenReturn(List.of(
                new Object[] {1L, SavingsContribution.Type.DEPOSIT, new BigDecimal("8000000")},
                new Object[] {1L, SavingsContribution.Type.WITHDRAW, new BigDecimal("500000")}));
        when(plannedRepository.sumByFamilyAndStatus(null, PlannedExpense.Status.PENDING)).thenReturn(new BigDecimal("7000000"));
        when(plannedRepository.countByFamily_IdAndStatus(null, PlannedExpense.Status.PENDING)).thenReturn(2L);
        when(goldPurchaseRepository.sumCostInPeriod(eq(null), any(), any())).thenReturn(new BigDecimal("30000000"));

        var summary = planningService.summary("member", "2026-11");

        assertEquals(new BigDecimal("7000000"), summary.pendingTotal());
        assertEquals(2L, summary.pendingCount());
        assertEquals(new BigDecimal("2500000"), summary.savingsNetInMonth());
        assertEquals(new BigDecimal("7500000"), summary.totalSaved());
        assertEquals(new BigDecimal("30000000"), summary.goldSpentInMonth());
    }
}
