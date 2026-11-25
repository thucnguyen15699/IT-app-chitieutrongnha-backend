package vn.nhaminh.api.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import vn.nhaminh.api.planning.PlannedExpenseRepository;
import vn.nhaminh.api.transaction.TransactionDtos.CreateCategoryRequest;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock private CurrentFamilyService currentFamily;
    @Mock private ExpenseCategoryRepository categoryRepository;
    @Mock private FamilyTransactionRepository transactionRepository;
    @Mock private PlannedExpenseRepository plannedRepository;
    @InjectMocks private CategoryService categoryService;

    @Test
    void createsFamilyCategoryWithTrimmedName() {
        Family family = mock(Family.class);
        when(family.getId()).thenReturn(10L);
        when(currentFamily.findFamily("member")).thenReturn(family);
        when(categoryRepository.existsAvailableName(10L, "Thú cưng", TransactionType.EXPENSE)).thenReturn(false);
        when(categoryRepository.save(any(ExpenseCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = categoryService.create("member", new CreateCategoryRequest("  Thú cưng ", TransactionType.EXPENSE));

        assertEquals("Thú cưng", created.name());
        assertTrue(created.custom());
    }

    @Test
    void rejectsDuplicateCategoryName() {
        Family family = mock(Family.class);
        when(family.getId()).thenReturn(10L);
        when(currentFamily.findFamily("member")).thenReturn(family);
        when(categoryRepository.existsAvailableName(10L, "Ăn uống", TransactionType.EXPENSE)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> categoryService.create("member", new CreateCategoryRequest("Ăn uống", TransactionType.EXPENSE)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void cannotDeleteDefaultCategory() {
        when(currentFamily.findFamily("member")).thenReturn(new Family("Nhà mình"));
        when(categoryRepository.findWithFamilyById(1L))
                .thenReturn(Optional.of(new ExpenseCategory(null, "Ăn uống", TransactionType.EXPENSE)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> categoryService.delete("member", 1L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void cannotDeleteCategoryInUse() {
        Family family = mock(Family.class);
        when(family.getId()).thenReturn(10L);
        when(currentFamily.findFamily("member")).thenReturn(family);
        when(categoryRepository.findWithFamilyById(7L))
                .thenReturn(Optional.of(new ExpenseCategory(family, "Thú cưng", TransactionType.EXPENSE)));
        when(transactionRepository.existsByCategory_Id(7L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> categoryService.delete("member", 7L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(categoryRepository, never()).delete(any());
    }
}
