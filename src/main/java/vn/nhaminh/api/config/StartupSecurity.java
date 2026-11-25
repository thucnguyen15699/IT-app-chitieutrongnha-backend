package vn.nhaminh.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.nhaminh.api.user.UserRepository;

/**
 * Chạy khi khởi động:
 * 1. Có ADMIN_PASSWORD → đặt mật khẩu tài khoản "admin" theo biến này (thay mật khẩu mẫu 12345).
 * 2. Ở test/prd (app.security.enforce-strong-secrets=true): từ chối khởi động nếu
 *    JWT_SECRET yếu/là giá trị dev, hoặc admin vẫn dùng mật khẩu mẫu.
 */
@Component
public class StartupSecurity implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupSecurity.class);
    static final String SAMPLE_ADMIN_PASSWORD = "12345";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final String jwtSecret;
    private final boolean enforce;

    public StartupSecurity(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.security.enforce-strong-secrets:false}") boolean enforce) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.jwtSecret = jwtSecret;
        this.enforce = enforce;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByUsernameIgnoreCase("admin").ifPresent(admin -> {
            if (!adminPassword.isBlank() && !passwordEncoder.matches(adminPassword, admin.getPasswordHash())) {
                admin.changePasswordHash(passwordEncoder.encode(adminPassword));
                userRepository.save(admin);
                log.info("Đã đặt mật khẩu tài khoản admin theo biến ADMIN_PASSWORD.");
            }
            if (enforce && passwordEncoder.matches(SAMPLE_ADMIN_PASSWORD, admin.getPasswordHash())) {
                throw new IllegalStateException(
                        "Tài khoản admin vẫn dùng mật khẩu mẫu. Đặt biến ADMIN_PASSWORD (ít nhất 12 ký tự) rồi khởi động lại.");
            }
        });
        if (enforce) {
            if (jwtSecret == null || jwtSecret.length() < 32 || jwtSecret.contains("dev-only")) {
                throw new IllegalStateException(
                        "JWT_SECRET quá yếu hoặc là giá trị dev. Tạo chuỗi ngẫu nhiên ≥ 32 ký tự (vd: openssl rand -base64 48).");
            }
            if (!adminPassword.isBlank() && adminPassword.length() < 12) {
                throw new IllegalStateException("ADMIN_PASSWORD cần ít nhất 12 ký tự ở môi trường test/prd.");
            }
        }
    }
}
