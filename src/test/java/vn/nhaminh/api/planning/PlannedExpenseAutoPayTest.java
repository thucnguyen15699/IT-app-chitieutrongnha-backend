package vn.nhaminh.api.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.nhaminh.api.family.CurrentFamilyService;
import vn.nhaminh.api.family.Family;
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

/** Khoản sắp chi đến hạn tự chuyển thành khoản đã chi. */
class PlannedExpenseAutoPayTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    private final PlannedExpenseRepository plannedRepository = mock(PlannedExpenseRepository.class);
    private final FamilyTransactionRepository transactionRepository = mock(FamilyTransactionRepository.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final PlanningService planningService = new PlanningService(
            mock(CurrentFamilyService.class), plannedRepository, mock(SavingsGoalRepository.class),
            mock(SavingsContributionRepository.class), mock(ExpenseCategoryRepository.class),
            mock(SpendingTargetRepository.class), transactionRepository, transactionService,
            mock(GoldPurchaseRepository.class));

    private final Family family = new Family("Nhà mình");
    private final User mom = new User("me", "Mẹ", null, null, "x", true);

    private PlannedExpense planned(LocalDate dueOn) {
        ExpenseCategory education = mock(ExpenseCategory.class);
        when(education.getId()).thenReturn(6L);
        return new PlannedExpense(family, education, null, mom, new BigDecimal("5000000"), "Học phí kỳ 2", dueOn);
    }

    @Test
    void dueExpenseBecomesTransactionOnItsDueDateInItsOwnFamily() {
        PlannedExpense tuition = planned(TODAY);
        when(plannedRepository.findForAutoPay(3L)).thenReturn(Optional.of(tuition));
        when(transactionService.createInFamily(any(), any(), any(), anyBoolean())).thenReturn(new TransactionView(
                99L, TransactionType.EXPENSE, 6L, "Giáo dục", null, "Cả nhà", new BigDecimal("5000000"), "Học phí kỳ 2", TODAY));
        when(transactionRepository.getReferenceById(99L)).thenReturn(mock(FamilyTransaction.class));

        assertTrue(planningService.autoPayDue(3L, TODAY));

        ArgumentCaptor<TransactionRequest> request = ArgumentCaptor.forClass(TransactionRequest.class);
        // Gia đình của khoản sắp chi + người đã lập khoản đó; đánh dấu là tự động.
        verify(transactionService).createInFamily(eq(mom), eq(family), request.capture(), eq(true));
        assertEquals(TODAY, request.getValue().occurredOn());          // ghi đúng ngày hạn
        assertEquals(new BigDecimal("5000000"), request.getValue().amount());
        assertEquals(PlannedExpense.Status.PAID, tuition.getStatus());
    }

    @Test
    void overdueExpenseIsRecordedOnTheOriginalDueDate() {
        PlannedExpense rent = planned(TODAY.minusDays(3)); // server tắt mấy hôm
        when(plannedRepository.findForAutoPay(4L)).thenReturn(Optional.of(rent));
        when(transactionService.createInFamily(any(), any(), any(), anyBoolean())).thenReturn(new TransactionView(
                100L, TransactionType.EXPENSE, 6L, "Nhà cửa", null, "Cả nhà", BigDecimal.ONE, "Tiền nhà", TODAY.minusDays(3)));

        planningService.autoPayDue(4L, TODAY);

        ArgumentCaptor<TransactionRequest> request = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).createInFamily(any(), any(), request.capture(), eq(true));
        assertEquals(TODAY.minusDays(3), request.getValue().occurredOn());
    }

    @Test
    void notYetDueOrAlreadyPaidExpensesAreLeftAlone() {
        PlannedExpense tomorrow = planned(TODAY.plusDays(1));
        when(plannedRepository.findForAutoPay(5L)).thenReturn(Optional.of(tomorrow));
        PlannedExpense alreadyPaid = planned(TODAY);
        alreadyPaid.markPaid(mock(FamilyTransaction.class));
        when(plannedRepository.findForAutoPay(6L)).thenReturn(Optional.of(alreadyPaid));

        assertFalse(planningService.autoPayDue(5L, TODAY));
        assertFalse(planningService.autoPayDue(6L, TODAY)); // chạy lại không ghi trùng
        verify(transactionService, never()).createInFamily(any(), any(), any(), anyBoolean());
    }

    @Test
    void oneFailingExpenseDoesNotStopTheOthers() {
        PlanningService service = mock(PlanningService.class);
        when(plannedRepository.findDuePendingIds(TODAY)).thenReturn(List.of(1L, 2L, 3L));
        when(service.autoPayDue(1L, TODAY)).thenReturn(true);
        when(service.autoPayDue(2L, TODAY)).thenThrow(new IllegalStateException("danh mục lỗi"));
        when(service.autoPayDue(3L, TODAY)).thenReturn(true);
        Clock vietnamMorning = Clock.fixed(TODAY.atTime(0, 5).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));

        int paid = new PlannedExpenseAutoPayer(plannedRepository, service, true, vietnamMorning).run();

        assertEquals(2, paid);
    }

    @Test
    void canBeSwitchedOff() {
        PlanningService service = mock(PlanningService.class);
        assertEquals(0, new PlannedExpenseAutoPayer(plannedRepository, service, false, Clock.systemDefaultZone()).run());
        verify(plannedRepository, never()).findDuePendingIds(any());
    }
}
