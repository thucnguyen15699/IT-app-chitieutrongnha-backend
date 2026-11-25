package vn.nhaminh.api.family;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

/**
 * Xác định gia đình mà yêu cầu hiện tại đang thao tác.
 *
 * <p>Người dùng thường: luôn là gia đình của chính họ, nên mỗi gia đình chỉ thấy dữ liệu của mình.
 * Quản trị viên: có thể gửi header {@code X-Family-Id} để XEM dữ liệu một gia đình khác;
 * chỉ áp dụng cho yêu cầu GET, mọi thao tác ghi vào gia đình khác đều bị chặn.
 */
@Service
public class CurrentFamilyService {
    public static final String FAMILY_HEADER = "X-Family-Id";

    private final UserRepository userRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final FamilyRepository familyRepository;

    public CurrentFamilyService(
            UserRepository userRepository,
            FamilyMembershipRepository membershipRepository,
            FamilyRepository familyRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.familyRepository = familyRepository;
    }

    public User findUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn tồn tại."));
    }

    public Family findFamily(String username) {
        return findFamily(findUser(username));
    }

    /** Gia đình đang thao tác: gia đình của người dùng, hoặc gia đình admin chọn để xem. */
    public Family findFamily(User user) {
        Optional<Family> own = findOwnFamily(user);
        Long requested = adminRequestedFamilyId();
        if (requested != null && own.map(family -> !requested.equals(family.getId())).orElse(true)) {
            HttpServletRequest request = currentRequest();
            if (request != null && !"GET".equalsIgnoreCase(request.getMethod())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Quản trị viên chỉ được xem dữ liệu của gia đình khác, không được thay đổi.");
            }
            return familyRepository.findById(requested)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gia đình."));
        }
        return own.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Tài khoản chưa thuộc gia đình nào. Hãy tạo gia đình hoặc nhận lời mời."));
    }

    /** Gia đình thật của người dùng (bỏ qua lựa chọn của admin). */
    public Optional<Family> findOwnFamily(User user) {
        return membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(user.getId()).map(FamilyMembership::getFamily);
    }

    public Optional<FamilyMembership> findOwnMembership(User user) {
        return membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(user.getId());
    }

    /** Id gia đình admin muốn xem; null nếu không phải admin hoặc không gửi header. */
    private Long adminRequestedFamilyId() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String header = request.getHeader(FAMILY_HEADER);
        if (header == null || header.isBlank() || !isAdmin()) return null;
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã gia đình không hợp lệ.");
        }
    }

    private static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
