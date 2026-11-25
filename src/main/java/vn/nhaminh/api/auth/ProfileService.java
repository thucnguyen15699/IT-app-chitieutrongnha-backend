package vn.nhaminh.api.auth;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.auth.AuthDtos.ProfileUpdateResponse;
import vn.nhaminh.api.auth.AuthDtos.UpdateProfileRequest;
import vn.nhaminh.api.user.PhoneNumbers;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

/**
 * Sửa thông tin cá nhân.
 * - Số điện thoại: cập nhật ngay (không cần OTP).
 * - Email: phải xác minh qua thư; email cũ vẫn dùng được cho tới khi xác minh xong.
 */
@Service
public class ProfileService {
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final AuthService authService;

    public ProfileService(
            UserRepository userRepository,
            EmailVerificationService emailVerificationService,
            AuthService authService) {
        this.userRepository = userRepository;
        this.emailVerificationService = emailVerificationService;
        this.authService = authService;
    }

    @Transactional
    public ProfileUpdateResponse update(String username, UpdateProfileRequest request) {
        User user = findUser(username);
        String name = request.name().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (name.isEmpty() || phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập đủ họ tên, email và số điện thoại.");
        }
        if (userRepository.existsByPhoneNumberAndIdNot(phone, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại này đã được tài khoản khác sử dụng.");
        }

        boolean emailChanged = !email.equals(user.getEmail());
        if (emailChanged && userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được tài khoản khác sử dụng.");
        }

        user.updateName(name);
        user.updatePhoneNumber(phone);
        String message = "Đã lưu thông tin cá nhân.";
        if (emailChanged || user.getPendingEmail() != null) {
            user.requestEmailChange(email); // nhập lại đúng email cũ = hủy yêu cầu đổi
            if (emailChanged) {
                userRepository.save(user);
                // Không gửi được thư thì báo lỗi và hoàn tác toàn bộ (giao dịch rollback).
                emailVerificationService.issue(user);
                message = emailVerificationService.isEnabled()
                        ? "Đã lưu. Vui lòng mở thư gửi tới " + email
                                + " để xác minh; email mới chỉ có hiệu lực sau khi xác minh."
                        : "Đã lưu thông tin và cập nhật email mới.";
            }
        }
        userRepository.save(user);
        return new ProfileUpdateResponse(authService.currentUser(username), message);
    }

    @Transactional
    public String resendVerification(String username) {
        User user = findUser(username);
        if (user.getPendingEmail() == null && (user.getEmail() == null || user.isEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không có email nào đang chờ xác minh.");
        }
        emailVerificationService.issue(user);
        return "Đã gửi lại thư xác minh tới " + user.emailToVerify() + ".";
    }

    private User findUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn tồn tại."));
    }
}
