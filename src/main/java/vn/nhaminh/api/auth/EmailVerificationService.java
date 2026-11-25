package vn.nhaminh.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@Service
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String senderAddress;
    private final String mailUsername;
    private final String frontendUrl;
    private final boolean verificationEnabled;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${app.mail.from:}") String senderAddress,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.mail.verification-enabled:false}") boolean verificationEnabled) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSenders = mailSenders;
        this.senderAddress = senderAddress;
        this.mailUsername = mailUsername;
        this.frontendUrl = frontendUrl;
        this.verificationEnabled = verificationEnabled;
    }

    /** false = đang tắt xác minh email (để test khi chưa có SMTP): email được coi là đã xác minh ngay. */
    public boolean isEnabled() {
        return verificationEnabled;
    }

    /**
     * Bật xác minh: tạo token và gửi thư.
     * Tắt xác minh: không gửi thư, xác minh ngay (email mới thay email cũ luôn).
     */
    @Transactional
    public void issue(User user) {
        if (!verificationEnabled) {
            user.verifyEmail();
            userRepository.save(user);
            return;
        }
        String rawToken = newToken();
        tokenRepository.deleteByUser_Id(user.getId());
        tokenRepository.save(new EmailVerificationToken(
                user, hash(rawToken), Instant.now().plus(TOKEN_LIFETIME)));
        sendVerificationEmail(user, rawToken);
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Liên kết xác minh không hợp lệ hoặc đã được sử dụng."));
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            tokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.GONE, "Liên kết xác minh đã hết hạn.");
        }

        User user = token.getUser();
        // Đang đổi email: email mới có thể đã bị tài khoản khác dùng trong lúc chờ xác minh.
        if (user.getPendingEmail() != null
                && userRepository.existsByEmailIgnoreCaseAndIdNot(user.getPendingEmail(), user.getId())) {
            tokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được tài khoản khác sử dụng.");
        }
        user.verifyEmail();
        userRepository.save(user);
        tokenRepository.delete(token);
    }

    private void sendVerificationEmail(User user, String rawToken) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        String from = senderAddress.isBlank() ? mailUsername : senderAddress;
        if (sender == null || from.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình SMTP để gửi email xác minh.");
        }

        String baseUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.emailToVerify());
        message.setSubject("Xác minh tài khoản NhaMinh");
        message.setText("Xin chào " + user.getName() + ",\n\n"
                + "Mở liên kết sau để xác minh email (có hiệu lực trong 24 giờ):\n"
                + baseUrl + "/?verify=" + rawToken + "\n\n"
                + "Nếu bạn không đăng ký tài khoản này, hãy bỏ qua email.");
        try {
            sender.send(message);
        } catch (MailException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Không gửi được email xác minh. Kiểm tra cấu hình SMTP.", exception);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for verification token hashing.", exception);
        }
    }
}
