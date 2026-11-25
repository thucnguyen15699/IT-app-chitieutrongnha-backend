package vn.nhaminh.api.planning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByFamily_IdOrderByCreatedAtAsc(Long familyId);

    Optional<SavingsGoal> findByIdAndFamily_Id(Long id, Long familyId);
}
