package vn.nhaminh.api.family;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMembershipRepository extends JpaRepository<FamilyMembership, Long> {
    Optional<FamilyMembership> findFirstByUserIdOrderByJoinedAtAsc(Long userId);

    List<FamilyMembership> findByUserId(Long userId);

    @Query("""
            select m from FamilyMembership m
            join fetch m.user
            where m.family.id = :familyId
            order by m.joinedAt
            """)
    List<FamilyMembership> findMembers(@Param("familyId") Long familyId);
}
