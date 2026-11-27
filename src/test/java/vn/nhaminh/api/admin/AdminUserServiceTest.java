package vn.nhaminh.api.admin;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.nhaminh.api.admin.AdminDtos.CreateUserRequest;
import vn.nhaminh.api.auth.Role;
import vn.nhaminh.api.auth.RoleRepository;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.family.FamilyMembership;
import vn.nhaminh.api.family.FamilyMembershipRepository;
import vn.nhaminh.api.family.FamilyRepository;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMembershipRepository membershipRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AdminUserService adminUserService;

    @Test
    void adminCanCreateVerifiedUserWithoutEmail() {
        Role memberRole = org.mockito.Mockito.mock(Role.class);
        when(memberRole.getCode()).thenReturn("MEMBER");
        when(userRepository.existsByUsernameIgnoreCase("thanhvien")).thenReturn(false);
        when(roleRepository.findByCode("MEMBER")).thenReturn(Optional.of(memberRole));
        when(familyRepository.save(any(Family.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("initial-password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(FamilyMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var user = adminUserService.createUser(new CreateUserRequest(
                "Thành viên", "ThanhVien", "", "", "initial-password", null, "Gia đình mới"));

        assertNull(user.email());
        assertTrue(user.emailVerified());
        assertTrue(user.roles().contains("MEMBER"));
    }

    @Test
    void adminCanResetAnotherUsersPassword() {
        User user = new User("thanhvien", "Thành viên", null, null, "old-hash", true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("reset-password")).thenReturn("new-hash");

        adminUserService.resetPassword(7L, "reset-password");

        org.junit.jupiter.api.Assertions.assertEquals("new-hash", user.getPasswordHash());
    }
}
