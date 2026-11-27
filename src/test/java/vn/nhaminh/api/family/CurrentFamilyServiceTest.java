package vn.nhaminh.api.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

/** Mỗi gia đình chỉ thấy dữ liệu của mình; chỉ admin được XEM gia đình khác. */
@ExtendWith(MockitoExtension.class)
class CurrentFamilyServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private FamilyMembershipRepository membershipRepository;
    @Mock private FamilyRepository familyRepository;
    @InjectMocks private CurrentFamilyService currentFamily;

    private final User user = new User("member", "Member", null, null, "encoded", true);
    private final Family own = new Family("Nhà mình");

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void request(String method, String familyHeader, String... roles) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/transactions");
        if (familyHeader != null) request.addHeader(CurrentFamilyService.FAMILY_HEADER, familyHeader);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "member", null, java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void withoutHeaderUsesFirstJoinedFamily() {
        request("GET", null, "ROLE_MEMBER");
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(own, user, "OWNER")));

        assertSame(own, currentFamily.findFamily(user));
    }

    @Test
    void memberCanSwitchToAnotherFamilyTheyJoinedAndWriteThere() {
        request("POST", "99", "ROLE_MEMBER");
        Family grandparents = new Family("Bên nội");
        when(membershipRepository.findByFamily_IdAndUser_Id(99L, null))
                .thenReturn(Optional.of(new FamilyMembership(grandparents, user, "MEMBER")));

        assertSame(grandparents, currentFamily.findFamily(user));
    }

    @Test
    void regularUserCannotOpenFamilyTheyWereNotInvitedTo() {
        request("GET", "99", "ROLE_MEMBER");
        when(membershipRepository.findByFamily_IdAndUser_Id(99L, null)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> currentFamily.findFamily(user));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void adminCanViewAnotherFamily() {
        request("GET", "99", "ROLE_ADMIN");
        Family other = mock(Family.class);
        when(membershipRepository.findByFamily_IdAndUser_Id(99L, null)).thenReturn(Optional.empty());
        when(familyRepository.findById(99L)).thenReturn(Optional.of(other));

        assertSame(other, currentFamily.findFamily(user));
    }

    @Test
    void adminCannotWriteIntoAnotherFamily() {
        request("POST", "99", "ROLE_ADMIN");
        when(membershipRepository.findByFamily_IdAndUser_Id(99L, null)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> currentFamily.findFamily(user));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void userWithoutFamilyGetsClearError() {
        request("GET", null, "ROLE_MEMBER");
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> currentFamily.findFamily(user));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void phoneNumbersAreNormalizedForMatching() {
        assertEquals("0912345678", vn.nhaminh.api.user.PhoneNumbers.normalize("+84 912 345 678"));
        assertEquals("0912345678", vn.nhaminh.api.user.PhoneNumbers.normalize("0912.345.678"));
        assertEquals(null, vn.nhaminh.api.user.PhoneNumbers.normalize("  "));
        assertThrows(ResponseStatusException.class, () -> vn.nhaminh.api.user.PhoneNumbers.normalize("12ab"));
        assertEquals(List.of(), List.of());
    }
}
