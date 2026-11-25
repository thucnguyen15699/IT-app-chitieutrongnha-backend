package vn.nhaminh.api.transaction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    @Query("""
            select category from ExpenseCategory category
            where category.type = :type
              and (category.family is null or category.family.id = :familyId)
            order by category.name
            """)
    List<ExpenseCategory> findAvailableCategories(
            @Param("familyId") Long familyId, @Param("type") TransactionType type);

    @Query("""
            select category from ExpenseCategory category
            left join fetch category.family
            where category.id = :id
            """)
    Optional<ExpenseCategory> findWithFamilyById(@Param("id") Long id);

    @Query("""
            select count(category) > 0 from ExpenseCategory category
            where lower(category.name) = lower(:name)
              and category.type = :type
              and (category.family is null or category.family.id = :familyId)
            """)
    boolean existsAvailableName(
            @Param("familyId") Long familyId, @Param("name") String name, @Param("type") TransactionType type);
}
