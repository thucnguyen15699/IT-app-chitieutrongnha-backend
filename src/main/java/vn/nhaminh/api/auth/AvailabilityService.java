package vn.nhaminh.api.auth;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nhaminh.api.user.PhoneNumbers;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

/**
 * Kiểm tra nhanh khi người dùng đang gõ: định dạng và trùng lặp của tên đăng nhập, email, SĐT.
 * Khi đã đăng nhập (sửa thông tin cá nhân) thì bỏ qua chính tài khoản đó.
 */
@Service
public class AvailabilityService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");

    /** valid: đúng định dạng. available: chưa ai dùng. message: câu hiển thị dưới ô nhập. */
    public record FieldCheck(boolean valid, boolean available, String message) {}

    public record AvailabilityResponse(FieldCheck username, FieldCheck email, FieldCheck phoneNumber) {}

    private final UserRepository userRepository;

    public AvailabilityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse check(String username, String email, String phoneNumber, String currentUsername) {
        Long selfId = currentUsername == null ? null
                : userRepository.findByUsernameIgnoreCase(currentUsername).map(User::getId).orElse(null);
        return new AvailabilityResponse(
                isBlank(username) ? null : checkUsername(username.trim()),
                isBlank(email) ? null : checkEmail(email.trim().toLowerCase(Locale.ROOT), selfId),
                isBlank(phoneNumber) ? null : checkPhone(phoneNumber, selfId));
    }

    private FieldCheck checkUsername(String username) {
        if (!USERNAME.matcher(username).matches()) {
            return new FieldCheck(false, false, "Tên đăng nhập 3–50 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        }
        return userRepository.existsByUsernameIgnoreCase(username)
                ? new FieldCheck(true, false, "Tên đăng nhập này đã có người dùng.")
                : new FieldCheck(true, true, "Tên đăng nhập có thể sử dụng.");
    }

    private FieldCheck checkEmail(String email, Long selfId) {
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            return new FieldCheck(false, false, "Email chưa đúng định dạng (ví dụ: ten@email.com).");
        }
        boolean taken = selfId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, selfId);
        return taken
                ? new FieldCheck(true, false, "Email này đã được một tài khoản khác sử dụng.")
                : new FieldCheck(true, true, "Email có thể sử dụng.");
    }

    private FieldCheck checkPhone(String raw, Long selfId) {
        String phone = PhoneNumbers.normalizeOrNull(raw);
        if (phone == null) {
            return new FieldCheck(false, false, "Số điện thoại chưa đúng (cần 9–15 chữ số, ví dụ: 0912 345 678).");
        }
        boolean taken = selfId == null
                ? userRepository.existsByPhoneNumber(phone)
                : userRepository.existsByPhoneNumberAndIdNot(phone, selfId);
        return taken
                ? new FieldCheck(true, false, "Số điện thoại này đã được một tài khoản khác sử dụng.")
                : new FieldCheck(true, true, "Số điện thoại có thể sử dụng.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
