package vn.nhaminh.api.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.config.MailService;
import vn.nhaminh.api.family.FamilyDtos.InviteRequest;
import vn.nhaminh.api.user.User;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {
    @Mock private CurrentFamilyService currentFamily;
    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMembershipRepository membershipRepository;
    @Mock private FamilyInvitationRepository invitationRepository;
    @Mock private MailService mailService;
    @InjectMocks private FamilyService familyService;

    private Family family(long id, String name) {
        Family family = mock(Family.class);
        org.mockito.Mockito.lenient().when(family.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(family.getName()).thenReturn(name);
        return family;
    }

    private User user(long id, String name, String email, boolean verified, String phone) {
        User user = mock(User.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(user.getName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(user.getEmail()).thenReturn(email);
        org.mockito.Mockito.lenient().when(user.isEmailVerified()).thenReturn(verified);
        org.mockito.Mockito.lenient().when(user.getPhoneNumber()).thenReturn(phone);
        return user;
    }

    @Test
    void inviteRequiresEmailOrPhone() {
        User mom = user(1, "Mẹ", "me@example.test", true, null);
        Family home = family(10, "Nhà Mình");
        when(currentFamily.findUser("me")).thenReturn(mom);
        when(currentFamily.findOwnMembership(mom)).thenReturn(Optional.of(new FamilyMembership(home, mom, "OWNER")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> familyService.invite("me", new InviteRequest(" ", "", "Vào nhà nhé")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteByPhoneNormalizesNumberAndSendsNoEmail() {
        User mom = user(1, "Mẹ", "me@example.test", true, null);
        Family home = family(10, "Nhà Mình");
        when(currentFamily.findUser("me")).thenReturn(mom);
        when(currentFamily.findOwnMembership(mom)).thenReturn(Optional.of(new FamilyMembership(home, mom, "OWNER")));
        when(membershipRepository.findMembers(10L)).thenReturn(List.of(new FamilyMembership(home, mom, "OWNER")));
        when(invitationRepository.save(any(FamilyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = familyService.invite("me", new InviteRequest(null, "+84 912-345-678", null));

        ArgumentCaptor<FamilyInvitation> saved = ArgumentCaptor.forClass(FamilyInvitation.class);
        verify(invitationRepository).save(saved.capture());
        assertEquals("0912345678", saved.getValue().getPhoneNumber());
        assertFalse(result.emailSent());
        verify(mailService, never()).trySend(anyString(), anyString(), anyString());
    }

    @Test
    void inviteByEmailStillSavedWhenMailCannotBeSent() {
        User mom = user(1, "Mẹ", "me@example.test", true, null);
        Family home = family(10, "Nhà Mình");
        when(currentFamily.findUser("me")).thenReturn(mom);
        when(currentFamily.findOwnMembership(mom)).thenReturn(Optional.of(new FamilyMembership(home, mom, "OWNER")));
        when(membershipRepository.findMembers(10L)).thenReturn(List.of(new FamilyMembership(home, mom, "OWNER")));
        when(invitationRepository.save(any(FamilyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mailService.trySend(any(), any(), any())).thenReturn(false);

        var result = familyService.invite("me", new InviteRequest("Bo@Example.test", null, "Về nhà thôi"));

        assertEquals("bo@example.test", result.invitation().email());
        assertFalse(result.emailSent());
        assertTrue(result.note().contains("đăng nhập"));
    }

    @Test
    void rejectsInvitingSomeoneAlreadyInFamily() {
        User mom = user(1, "Mẹ", "me@example.test", true, null);
        User dad = user(2, "Bố", "bo@example.test", true, "0912345678");
        Family home = family(10, "Nhà Mình");
        when(currentFamily.findUser("me")).thenReturn(mom);
        when(currentFamily.findOwnMembership(mom)).thenReturn(Optional.of(new FamilyMembership(home, mom, "OWNER")));
        when(membershipRepository.findMembers(10L)).thenReturn(List.of(
                new FamilyMembership(home, mom, "OWNER"), new FamilyMembership(home, dad, "MEMBER")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> familyService.invite("me", new InviteRequest(null, "0912 345 678", null)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void incomingIgnoresUnverifiedEmailButMatchesPhone() {
        User dad = user(2, "Bố", "bo@example.test", false, "+84912345678");
        Family home = family(10, "Nhà Mình");
        User mom = user(1, "Mẹ", null, true, null);
        FamilyInvitation byPhone = new FamilyInvitation(home, mom, null, "0912345678", "Vào nhà nhé");
        when(currentFamily.findUser("bo")).thenReturn(dad);
        when(invitationRepository.findByPhoneNumberAndStatus("0912345678", FamilyInvitation.Status.PENDING))
                .thenReturn(List.of(byPhone));

        var incoming = familyService.incoming("bo");

        assertEquals(1, incoming.size());
        assertEquals("PHONE", incoming.getFirst().matchedBy());
        assertEquals("Nhà Mình", incoming.getFirst().familyName());
        verify(invitationRepository, never()).findByEmailIgnoreCaseAndStatus(any(), any());
    }

    @Test
    void acceptingAddsNewFamilyAndKeepsTheOldOne() {
        Family newHome = family(10, "Bên nội");
        User dad = user(2, "Bố", "bo@example.test", true, null);
        FamilyInvitation invitation = mock(FamilyInvitation.class);
        when(invitation.getId()).thenReturn(5L);
        when(invitation.getFamily()).thenReturn(newHome);
        when(currentFamily.findUser("bo")).thenReturn(dad);
        when(invitationRepository.findByEmailIgnoreCaseAndStatus("bo@example.test", FamilyInvitation.Status.PENDING))
                .thenReturn(List.of(invitation));
        when(membershipRepository.findByFamily_IdAndUser_Id(10L, 2L)).thenReturn(Optional.empty());

        Long joined = familyService.accept("bo", 5L);

        assertEquals(10L, joined);
        verify(membershipRepository, never()).delete(any());
        ArgumentCaptor<FamilyMembership> added = ArgumentCaptor.forClass(FamilyMembership.class);
        verify(membershipRepository).save(added.capture());
        assertEquals(newHome, added.getValue().getFamily());
        assertEquals("MEMBER", added.getValue().getRole());
        verify(invitation).respond(FamilyInvitation.Status.ACCEPTED, dad);
    }

    @Test
    void incomingHidesInvitationsToFamiliesAlreadyJoined() {
        Family home = family(10, "Nhà Mình");
        User dad = user(2, "Bố", "bo@example.test", true, null);
        User mom = user(1, "Mẹ", null, true, null);
        FamilyInvitation again = new FamilyInvitation(home, mom, "bo@example.test", null, null);
        when(currentFamily.findUser("bo")).thenReturn(dad);
        when(invitationRepository.findByEmailIgnoreCaseAndStatus("bo@example.test", FamilyInvitation.Status.PENDING))
                .thenReturn(List.of(again));
        when(membershipRepository.findByUserId(2L)).thenReturn(List.of(new FamilyMembership(home, dad, "MEMBER")));

        assertEquals(0, familyService.incoming("bo").size());
    }

    @Test
    void leavingAsOwnerPromotesEarliestRemainingMember() {
        Family home = family(20, "Bên ngoại");
        User dad = user(2, "Bố", null, true, null);
        User grandma = user(3, "Bà", null, true, null);
        FamilyMembership dadMembership = new FamilyMembership(home, dad, "OWNER");
        FamilyMembership grandmaMembership = new FamilyMembership(home, grandma, "MEMBER");
        when(currentFamily.findUser("bo")).thenReturn(dad);
        when(currentFamily.findOwnMembership(dad)).thenReturn(Optional.of(dadMembership));
        when(membershipRepository.findMembers(20L)).thenReturn(List.of(grandmaMembership));

        familyService.leave("bo");

        verify(membershipRepository).delete(dadMembership);
        assertEquals("OWNER", grandmaMembership.getRole());
    }

    @Test
    void cannotAcceptInvitationAddressedToSomeoneElse() {
        User stranger = user(9, "Người lạ", "la@example.test", true, null);
        when(currentFamily.findUser("la")).thenReturn(stranger);
        when(invitationRepository.findByEmailIgnoreCaseAndStatus("la@example.test", FamilyInvitation.Status.PENDING))
                .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> familyService.accept("la", 5L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(membershipRepository, never()).save(any());
    }
}
