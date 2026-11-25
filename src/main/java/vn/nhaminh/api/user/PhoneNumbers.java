package vn.nhaminh.api.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Chuẩn hóa số điện thoại để so khớp lời mời: "+84 912-345-678" → "0912345678". */
public final class PhoneNumbers {
    private PhoneNumbers() {}

    /** Chuẩn hóa; trả về null nếu rỗng. Ném lỗi 400 nếu không giống số điện thoại. */
    public static String normalize(String raw) {
        String normalized = normalizeOrNull(raw);
        if (raw != null && !raw.isBlank() && normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ.");
        }
        return normalized;
    }

    /** Như normalize nhưng trả null thay vì ném lỗi (dùng cho dữ liệu cũ). */
    public static String normalizeOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.length() >= 9 && digits.length() <= 15 ? digits : null;
    }
}
