package vn.nhaminh.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.auth.AuthDtos.LoginRequest;
import vn.nhaminh.api.auth.AuthDtos.RegisterRequest;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.family.FamilyMembership;
import vn.nhaminh.api.family.FamilyMembershipRepository;
import vn.nhaminh.api.family.FamilyRepository;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMembershipRepository membershipRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RoleRepository roleRepository;
    @Mock private EmailVerificationService emailVerificationService;
    @InjectMocks private AuthService authService;

    @Test
    void logsInAdminByUsernameWithoutEmail() {
        User admin = new User("admin", "Administrator", null, null, "encoded", true);
        Role adminRole = org.mockito.Mockito.mock(Role.class);
        when(adminRole.getCode()).thenReturn("ADMIN");
        admin.addRole(adminRole);
        Family family = new Family("Gia đình quản trị");

        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("12345", "encoded")).thenReturn(true);
        when(membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(null))
                .thenReturn(Optional.of(new FamilyMembership(family, admin, "OWNER")));
        when(jwtService.generateToken("admin")).thenReturn("signed-token");

        var response = authService.login(new LoginRequest("admin", "12345"));

        assertEquals("signed-token", response.token());
        assertEquals("admin", response.user().username());
        assertNull(response.user().email());
        assertEquals(java.util.List.of("ADMIN"), response.user().roles());
    }

    @Test
    void doesNotAllowLoginBeforeEmailVerification() {
        User pending = new User("pending", "Pending", "pending@example.test", null, "encoded", false);
        when(userRepository.findByUsernameIgnoreCase("pending")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("long-password", "encoded")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("pending", "long-password")));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void registrationCreatesUnverifiedUsernameAccountAndSendsVerification() {
        Role ownerRole = org.mockito.Mockito.mock(Role.class);
        when(userRepository.existsByUsernameIgnoreCase("minhanh")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("minhanh@example.test")).thenReturn(false);
        when(familyRepository.save(any(Family.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(passwordEncoder.encode("very-long-password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(FamilyMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest(
                "Minh Anh", "MinhAnh", "MinhAnh@example.test", "0900000000",
                "very-long-password", "Nhà Mình"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(emailVerificationService).issue(userCaptor.capture());
        assertEquals("minhanh", userCaptor.getValue().getUsername());
        assertFalse(userCaptor.getValue().isEmailVerified());
        assertEquals("Đã gửi liên kết xác minh đến email của bạn.", response.message());
    }
}
