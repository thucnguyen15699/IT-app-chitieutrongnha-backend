package vn.nhaminh.api.transaction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpendingTargetRepository extends JpaRepository<SpendingTarget, Long> {
    @Query("""
            select target from SpendingTarget target
            where target.family is null or target.family.id = :familyId
            order by target.sortOrder, target.name
            """)
    List<SpendingTarget> findAvailableTargets(@Param("familyId") Long familyId);

    @Query("""
            select target from SpendingTarget target
            left join fetch target.family
            where target.id = :id
            """)
    Optional<SpendingTarget> findWithFamilyById(@Param("id") Long id);

    @Query("""
            select count(target) > 0 from SpendingTarget target
            where lower(target.name) = lower(:name)
              and (target.family is null or target.family.id = :familyId)
            """)
    boolean existsAvailableName(@Param("familyId") Long familyId, @Param("name") String name);
}
