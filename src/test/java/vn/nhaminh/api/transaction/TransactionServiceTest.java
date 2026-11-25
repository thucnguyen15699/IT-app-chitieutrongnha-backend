package vn.nhaminh.api.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.family.FamilyMembership;
import vn.nhaminh.api.family.FamilyMembershipRepository;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionRequest;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private FamilyMembershipRepository membershipRepository;
    @Mock private ExpenseCategoryRepository categoryRepository;
    @Mock private FamilyTransactionRepository transactionRepository;
    @Mock private SpendingTargetRepository targetRepository;
    @Mock private vn.nhaminh.api.family.FamilyRepository familyRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    private TransactionService transactionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Dùng CurrentFamilyService thật trên các repository giả để giữ nguyên luồng tìm gia đình.
        var currentFamily = new vn.nhaminh.api.family.CurrentFamilyService(
                userRepository, membershipRepository, familyRepository);
        transactionService = new TransactionService(currentFamily, categoryRepository, transactionRepository, targetRepository, events);
    }

    @Test
    void createsExpenseWithCategoryNoteAndSelectedPastDate() {
        User user = new User("member", "Member", null, null, "encoded", true);
        Family family = new Family("Gia đình");
        ExpenseCategory category = new ExpenseCategory(null, "Đi chợ", TransactionType.EXPENSE);
        LocalDate occurredOn = LocalDate.now().minusDays(2);
        when(userRepository.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(family, user, "OWNER")));
        when(categoryRepository.findWithFamilyById(1L)).thenReturn(Optional.of(category));
        when(transactionRepository.save(any(FamilyTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var saved = transactionService.create("member", new TransactionRequest(
                TransactionType.EXPENSE, 1L, new BigDecimal("100000"), "Mua thịt", occurredOn, null));

        assertEquals(TransactionType.EXPENSE, saved.type());
        assertEquals("Đi chợ", saved.categoryName());
        assertEquals("Mua thịt", saved.note());
        assertEquals(occurredOn, saved.occurredOn());
        assertEquals("Cả nhà", saved.targetName());
        verify(transactionRepository).save(any(FamilyTransaction.class));
        // Có giao dịch mới thì phát sự kiện để báo cho các thành viên khác.
        verify(events).publishEvent(any(TransactionCreatedEvent.class));
    }

    @Test
    void createsExpenseForSelectedTarget() {
        User user = new User("member", "Member", null, null, "encoded", true);
        Family family = new Family("Gia đình");
        ExpenseCategory category = new ExpenseCategory(null, "Ăn uống", TransactionType.EXPENSE);
        SpendingTarget child = new SpendingTarget(null, "Con", 30);
        when(userRepository.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(family, user, "OWNER")));
        when(categoryRepository.findWithFamilyById(1L)).thenReturn(Optional.of(category));
        when(targetRepository.findWithFamilyById(4L)).thenReturn(Optional.of(child));
        when(transactionRepository.save(any(FamilyTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var saved = transactionService.create("member", new TransactionRequest(
                TransactionType.EXPENSE, 1L, new BigDecimal("250000"), "Mua sữa", LocalDate.now(), 4L));

        assertEquals("Con", saved.targetName());
    }

    @Test
    void rejectsTargetOwnedByAnotherFamily() {
        User user = new User("member", "Member", null, null, "encoded", true);
        Family userFamily = org.mockito.Mockito.mock(Family.class);
        Family otherFamily = org.mockito.Mockito.mock(Family.class);
        when(userFamily.getId()).thenReturn(10L);
        when(otherFamily.getId()).thenReturn(20L);
        when(userRepository.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(userFamily, user, "OWNER")));
        when(categoryRepository.findWithFamilyById(1L))
                .thenReturn(Optional.of(new ExpenseCategory(null, "Mua sắm", TransactionType.EXPENSE)));
        when(targetRepository.findWithFamilyById(9L))
                .thenReturn(Optional.of(new SpendingTarget(otherFamily, "Ông bà", 100)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.create("member", new TransactionRequest(
                        TransactionType.EXPENSE, 1L, new BigDecimal("300000"), "Áo", LocalDate.now(), 9L)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void rejectsExpenseWithoutNote() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.create("member", new TransactionRequest(
                        TransactionType.EXPENSE, 1L, new BigDecimal("30000"), "  ", LocalDate.now(), null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsFutureTransactionDates() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.create("member", new TransactionRequest(
                        TransactionType.INCOME, 1L, new BigDecimal("1000000"), null, LocalDate.now().plusDays(1), null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsCategoryFromDifferentTransactionType() {
        User user = new User("member", "Member", null, null, "encoded", true);
        Family family = new Family("Gia đình");
        ExpenseCategory incomeCategory = new ExpenseCategory(null, "Lương", TransactionType.INCOME);
        when(userRepository.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(family, user, "OWNER")));
        when(categoryRepository.findWithFamilyById(1L)).thenReturn(Optional.of(incomeCategory));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.create("member", new TransactionRequest(
                        TransactionType.EXPENSE, 1L, new BigDecimal("30000"), "Mua thịt", LocalDate.now(), null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsFamilyCategoryOwnedByAnotherFamily() {
        User user = new User("member", "Member", null, null, "encoded", true);
        Family userFamily = org.mockito.Mockito.mock(Family.class);
        Family categoryFamily = org.mockito.Mockito.mock(Family.class);
        when(userFamily.getId()).thenReturn(10L);
        when(categoryFamily.getId()).thenReturn(20L);
        ExpenseCategory category = new ExpenseCategory(categoryFamily, "Đồ ăn", TransactionType.EXPENSE);
        when(userRepository.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(userFamily, user, "OWNER")));
        when(categoryRepository.findWithFamilyById(1L)).thenReturn(Optional.of(category));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.create("member", new TransactionRequest(
                        TransactionType.EXPENSE, 1L, new BigDecimal("30000"), "Mua thịt", LocalDate.now(), null)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void updatesTransactionAmountCategoryNoteAndDate() {
        User user = new User("member", "Member", null, null, "encoded", true);
        Family family = new Family("Gia đình");
        ExpenseCategory incomeCategory = new ExpenseCategory(null, "Lương", TransactionType.INCOME);
        FamilyTransaction transaction = new FamilyTransaction(
                family,
                new ExpenseCategory(null, "Đi chợ", TransactionType.EXPENSE),
                user,
                TransactionType.EXPENSE,
                new BigDecimal("30000"),
                "Mua thịt",
                LocalDate.now().minusDays(1).atStartOfDay());
        LocalDate occurredOn = LocalDate.now().minusDays(3);
        when(userRepository.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(family, user, "OWNER")));
        when(transactionRepository.findByIdAndFamily_Id(8L, null)).thenReturn(Optional.of(transaction));
        when(categoryRepository.findWithFamilyById(2L)).thenReturn(Optional.of(incomeCategory));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        var updated = transactionService.update("member", 8L, new TransactionRequest(
                TransactionType.INCOME, 2L, new BigDecimal("2500000"), "Lương tháng", occurredOn, null));

        assertEquals(TransactionType.INCOME, updated.type());
        assertEquals("Lương", updated.categoryName());
        assertEquals(new BigDecimal("2500000"), updated.amount());
        assertEquals("Lương tháng", updated.note());
        assertEquals(occurredOn, updated.occurredOn());
    }

    @Test
    void cannotUpdateTransactionOutsideCurrentFamily() {
        User user = new User("member", "Member", null, null, "encoded", true);
        Family family = new Family("Gia đình");
        when(userRepository.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(family, user, "OWNER")));
        when(transactionRepository.findByIdAndFamily_Id(99L, null)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.update("member", 99L, new TransactionRequest(
                        TransactionType.EXPENSE, 1L, new BigDecimal("30000"), "Mua thịt", LocalDate.now(), null)));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
