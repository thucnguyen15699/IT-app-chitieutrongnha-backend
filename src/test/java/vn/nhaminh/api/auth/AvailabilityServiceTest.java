package vn.nhaminh.api.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {
    @Mock private UserRepository userRepository;
    @InjectMocks private AvailabilityService availabilityService;

    @Test
    void reportsTakenEmailAndPhoneForNewRegistration() {
        when(userRepository.existsByEmailIgnoreCase("me@example.test")).thenReturn(true);
        when(userRepository.existsByPhoneNumber("0912345678")).thenReturn(false);

        var result = availabilityService.check(null, " Me@Example.test ", "+84 912 345 678", null);

        assertNull(result.username());
        assertTrue(result.email().valid());
        assertFalse(result.email().available());
        assertTrue(result.phoneNumber().available());
    }

    @Test
    void reportsInvalidFormatsWithoutQueryingDatabase() {
        var result = availabilityService.check("a!", "khong-phai-email", "12ab", null);

        assertFalse(result.username().valid());
        assertFalse(result.email().valid());
        assertFalse(result.phoneNumber().valid());
    }

    @Test
    void ownEmailIsNotReportedAsTakenWhenEditingProfile() {
        User me = mock(User.class);
        when(me.getId()).thenReturn(7L);
        when(userRepository.findByUsernameIgnoreCase("bo")).thenReturn(Optional.of(me));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("bo@example.test", 7L)).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndIdNot("0912345678", 7L)).thenReturn(true);

        var result = availabilityService.check(null, "bo@example.test", "0912345678", "bo");

        assertTrue(result.email().available());
        assertFalse(result.phoneNumber().available());
    }
}
