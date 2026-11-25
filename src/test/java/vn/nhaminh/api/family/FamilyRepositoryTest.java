package vn.nhaminh.api.family;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vn.nhaminh.api.user.User;

@DataJpaTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:familytest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class FamilyRepositoryTest {
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMembershipRepository membershipRepository;
    @Autowired private FamilyInvitationRepository invitationRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void countsMembersAndFindsInvitations() {
        Family home = entityManager.persist(new Family("Nhà Mình"));
        Family empty = entityManager.persist(new Family("Nhà trống"));
        User mom = entityManager.persist(new User("me", "Mẹ", "me@example.test", null, "x", true));
        User dad = entityManager.persist(new User("bo", "Bố", null, "0912345678", "x", true));
        entityManager.persist(new FamilyMembership(home, mom, "OWNER"));
        entityManager.persist(new FamilyMembership(home, dad, "MEMBER"));
        entityManager.persist(new FamilyInvitation(home, mom, "con@example.test", null, null));
        entityManager.persist(new FamilyInvitation(home, mom, null, "0987654321", "Vào nhà nhé"));
        entityManager.flush();
        entityManager.clear();

        var rows = familyRepository.findAllWithMemberCount();
        assertEquals(2, rows.size());
        assertEquals("Nhà Mình", ((Family) rows.get(0)[0]).getName());
        assertEquals(2L, rows.get(0)[1]);
        assertEquals(0L, rows.get(1)[1]);

        var members = membershipRepository.findMembers(home.getId());
        assertEquals("Mẹ", members.getFirst().getUser().getName());
        assertEquals(0, membershipRepository.findMembers(empty.getId()).size());

        assertEquals(1, invitationRepository.findByEmailIgnoreCaseAndStatus("CON@example.test", FamilyInvitation.Status.PENDING).size());
        assertEquals(1, invitationRepository.findByPhoneNumberAndStatus("0987654321", FamilyInvitation.Status.PENDING).size());
        assertEquals(true, invitationRepository.existsByFamily_IdAndPhoneNumberAndStatus(
                home.getId(), "0987654321", FamilyInvitation.Status.PENDING));
    }
}
