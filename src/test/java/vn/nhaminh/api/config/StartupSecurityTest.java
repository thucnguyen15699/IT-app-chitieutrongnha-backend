package vn.nhaminh.api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

class StartupSecurityTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private static final String STRONG_SECRET = "a-very-long-random-production-secret-0123456789";

    private User adminWithPassword(String password) {
        return new User("admin", "Administrator", null, null, encoder.encode(password), true);
    }

    private StartupSecurity startup(User admin, String adminPassword, String jwtSecret, boolean enforce) {
        UserRepository repository = Mockito.mock(UserRepository.class);
        Mockito.when(repository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        return new StartupSecurity(repository, encoder, adminPassword, jwtSecret, enforce);
    }

    @Test
    void productionRefusesToStartWithSampleAdminPassword() {
        User admin = adminWithPassword("12345");
        assertThrows(IllegalStateException.class, () -> startup(admin, "", STRONG_SECRET, true).run(null));
    }

    @Test
    void productionRefusesWeakOrDevJwtSecret() {
        User admin = adminWithPassword("a-strong-admin-pass");
        assertThrows(IllegalStateException.class,
                () -> startup(admin, "", "dev-only-secret-key-change-before-deploying-please", true).run(null));
        assertThrows(IllegalStateException.class, () -> startup(admin, "", "short", true).run(null));
    }

    @Test
    void adminPasswordVariableReplacesSamplePassword() {
        User admin = adminWithPassword("12345");

        assertDoesNotThrow(() -> startup(admin, "Mat-khau-that-manh-2026", STRONG_SECRET, true).run(null));

        assertTrue(encoder.matches("Mat-khau-that-manh-2026", admin.getPasswordHash()));
    }

    @Test
    void devEnvironmentAllowsSampleValues() {
        User admin = adminWithPassword("12345");
        assertDoesNotThrow(() -> startup(admin, "", "dev-only-secret-key-change-before-deploying-please", false).run(null));
    }
}
