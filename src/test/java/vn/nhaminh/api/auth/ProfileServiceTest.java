package vn.nhaminh.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.auth.AuthDtos.ChangePasswordRequest;
import vn.nhaminh.api.auth.AuthDtos.UpdateProfileRequest;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private AuthService authService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private ProfileService profileService;

    @Test
    void changePasswordRequiresCorrectCurrentPassword() {
        User user = new User("bo", "Bố", "bo@example.test", null, "old-hash", true);
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "old-hash")).thenReturn(false);

        var error = assertThrows(ResponseStatusException.class,
                () -> profileService.changePassword("bo", new ChangePasswordRequest("wrong-pass", "new-password")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("old-hash", user.getPasswordHash());
    }

    @Test
    void changePasswordStoresNewHash() {
        User user = new User("bo", "Bố", "bo@example.test", null, "old-hash", true);
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        profileService.changePassword("bo", new ChangePasswordRequest("old-password", "new-password"));

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    void phoneIsSavedImmediatelyWithoutOtp() {
        User user = new User("bo", "Bố", "bo@example.test", null, "x", true);
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(user));

        var response = profileService.update("bo", new UpdateProfileRequest("Bố Minh", "bo@example.test", "+84 912 345 678"));

        assertEquals("0912345678", user.getPhoneNumber());
        assertEquals("Bố Minh", user.getName());
        assertNull(user.getPendingEmail());
        assertEquals("Đã lưu thông tin cá nhân.", response.message());
        verify(emailVerificationService, never()).issue(any());
    }

    @Test
    void newEmailWaitsForVerificationAndOldEmailKeepsWorking() {
        User user = new User("bo", "Bố", "bo@example.test", "0912345678", "x", true);
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(user));
        when(emailVerificationService.isEnabled()).thenReturn(true);

        var response = profileService.update("bo", new UpdateProfileRequest("Bố", "Bo.Moi@Example.test", "0912345678"));

        assertEquals("bo@example.test", user.getEmail()); // email cũ vẫn dùng được
        assertTrue(user.isEmailVerified());
        assertEquals("bo.moi@example.test", user.getPendingEmail());
        assertEquals("bo.moi@example.test", user.emailToVerify());
        assertTrue(response.message().contains("xác minh"));
        verify(emailVerificationService).issue(user);

        user.verifyEmail(); // bấm liên kết trong thư
        assertEquals("bo.moi@example.test", user.getEmail());
        assertNull(user.getPendingEmail());
    }

    @Test
    void userWithoutEmailMustVerifyTheEmailTheyAdd() {
        User user = new User("ba", "Bà", null, null, "x", true); // tài khoản admin tạo, chưa có email
        when(userRepository.findByUsernameIgnoreCase("ba")).thenReturn(Optional.of(user));

        profileService.update("ba", new UpdateProfileRequest("Bà", "ba@example.test", "0987654321"));

        assertNull(user.getEmail());
        assertEquals("ba@example.test", user.getPendingEmail());
        verify(emailVerificationService).issue(user);
    }

    @Test
    void rejectsPhoneUsedByAnotherAccount() {
        User user = new User("bo", "Bố", "bo@example.test", null, "x", true);
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(user));
        when(userRepository.existsByPhoneNumberAndIdNot("0912345678", null)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> profileService.update("bo", new UpdateProfileRequest("Bố", "bo@example.test", "0912345678")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void rejectsEmailUsedByAnotherAccount() {
        User user = new User("bo", "Bố", "bo@example.test", null, "x", true);
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("me@example.test", null)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> profileService.update("bo", new UpdateProfileRequest("Bố", "me@example.test", "0912345678")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertNull(user.getPendingEmail());
    }

    @Test
    void reenteringCurrentEmailCancelsPendingChange() {
        User user = new User("bo", "Bố", "bo@example.test", "0912345678", "x", true);
        user.requestEmailChange("nham@example.test");
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(user));

        profileService.update("bo", new UpdateProfileRequest("Bố", "bo@example.test", "0912345678"));

        assertNull(user.getPendingEmail());
        verify(emailVerificationService, never()).issue(any());
    }
}
