package vn.nhaminh.api.family;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, Long> {
    List<FamilyInvitation> findByFamily_IdAndStatusOrderByCreatedAtDesc(Long familyId, FamilyInvitation.Status status);

    List<FamilyInvitation> findByEmailIgnoreCaseAndStatus(String email, FamilyInvitation.Status status);

    List<FamilyInvitation> findByPhoneNumberAndStatus(String phoneNumber, FamilyInvitation.Status status);

    Optional<FamilyInvitation> findByIdAndFamily_Id(Long id, Long familyId);

    boolean existsByFamily_IdAndEmailIgnoreCaseAndStatus(Long familyId, String email, FamilyInvitation.Status status);

    boolean existsByFamily_IdAndPhoneNumberAndStatus(Long familyId, String phoneNumber, FamilyInvitation.Status status);
}
