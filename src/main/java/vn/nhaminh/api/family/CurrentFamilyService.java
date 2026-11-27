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

    /**
     * Gia đình đang thao tác.
     * <ul>
     *   <li>Có header {@code X-Family-Id} và người dùng là thành viên gia đình đó → dùng gia đình đó (đọc + ghi).</li>
     *   <li>Có header nhưng không phải thành viên: admin được XEM (chỉ GET); người khác bị từ chối.</li>
     *   <li>Không có header → gia đình tham gia sớm nhất.</li>
     * </ul>
     */
    public Family findFamily(User user) {
        Long requested = requestedFamilyId();
        if (requested != null) {
            Optional<FamilyMembership> membership = membershipRepository.findByFamily_IdAndUser_Id(requested, user.getId());
            if (membership.isPresent()) return membership.get().getFamily();
            if (!isAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bạn chưa là thành viên gia đình này. Cần được mời và đồng ý lời mời trước.");
            }
            HttpServletRequest request = currentRequest();
            if (request != null && !"GET".equalsIgnoreCase(request.getMethod())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Quản trị viên chỉ được xem dữ liệu của gia đình khác, không được thay đổi.");
            }
            return familyRepository.findById(requested)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gia đình."));
        }
        return findOwnFamily(user).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Tài khoản chưa thuộc gia đình nào. Hãy tạo gia đình hoặc nhận lời mời."));
    }

    /** Gia đình mặc định của người dùng: gia đình tham gia sớm nhất (bỏ qua lựa chọn qua header). */
    public Optional<Family> findOwnFamily(User user) {
        return membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(user.getId()).map(FamilyMembership::getFamily);
    }

    /**
     * Tư cách thành viên của người dùng trong gia đình ĐANG CHỌN (header {@code X-Family-Id}, mặc định gia đình đầu tiên).
     * Rỗng khi admin đang chỉ xem một gia đình mình không thuộc về.
     */
    public Optional<FamilyMembership> findOwnMembership(User user) {
        Long requested = requestedFamilyId();
        if (requested != null) return membershipRepository.findByFamily_IdAndUser_Id(requested, user.getId());
        return membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(user.getId());
    }

    /** Id gia đình được chọn qua header; null nếu không gửi. */
    private Long requestedFamilyId() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String header = request.getHeader(FAMILY_HEADER);
        if (header == null || header.isBlank()) return null;
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
