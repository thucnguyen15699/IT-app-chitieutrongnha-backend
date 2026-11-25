package vn.nhaminh.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Gửi email "cố gắng hết sức": không cấu hình SMTP hoặc gửi lỗi thì ghi log và trả về false. */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String senderAddress;
    private final String mailUsername;
    private final String frontendUrl;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${app.mail.from:}") String senderAddress,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSenders = mailSenders;
        this.senderAddress = senderAddress;
        this.mailUsername = mailUsername;
        this.frontendUrl = frontendUrl;
    }

    public String frontendBaseUrl() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public boolean trySend(String to, String subject, String text) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        String from = senderAddress.isBlank() ? mailUsername : senderAddress;
        if (sender == null || from.isBlank()) {
            log.warn("Chưa cấu hình SMTP, bỏ qua email \"{}\".", subject);
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            return true;
        } catch (MailException exception) {
            log.warn("Gửi email \"{}\" thất bại: {}", subject, exception.getMessage());
            return false;
        }
    }
}
