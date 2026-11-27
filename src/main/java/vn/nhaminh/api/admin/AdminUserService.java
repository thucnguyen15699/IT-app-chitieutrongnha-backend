package vn.nhaminh.api.admin;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.admin.AdminDtos.CreateUserRequest;
import vn.nhaminh.api.admin.AdminDtos.FamilySummary;
import vn.nhaminh.api.admin.AdminDtos.UserView;
import vn.nhaminh.api.auth.Role;
import vn.nhaminh.api.auth.RoleRepository;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.family.FamilyMembership;
import vn.nhaminh.api.family.FamilyMembershipRepository;
import vn.nhaminh.api.family.FamilyRepository;
import vn.nhaminh.api.user.PhoneNumbers;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            UserRepository userRepository,
            FamilyRepository familyRepository,
            FamilyMembershipRepository membershipRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.familyRepository = familyRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public UserView createUser(CreateUserRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = normalizeOptional(request.email());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập này đã được sử dụng.");
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được sử dụng.");
        }
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (phone != null && userRepository.existsByPhoneNumber(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại này đã được sử dụng.");
        }

        String newFamilyName = request.familyName() == null || request.familyName().isBlank()
                ? null : request.familyName().trim();
        if ((request.familyId() == null) == (newFamilyName == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chọn một gia đình có sẵn hoặc nhập tên gia đình mới (chỉ một trong hai).");
        }
        Family family;
        String role;
        if (request.familyId() != null) {
            family = familyRepository.findById(request.familyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gia đình không tồn tại."));
            // Gia đình chưa có ai (vd. mọi người đã rời đi) thì người mới làm chủ.
            role = membershipRepository.findMembers(family.getId()).isEmpty() ? "OWNER" : "MEMBER";
        } else {
            family = familyRepository.save(new Family(newFamilyName));
            role = "OWNER";
        }

        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseThrow(() -> new IllegalStateException("Role MEMBER chưa được khởi tạo."));
        User user = new User(
                username,
                request.name().trim(),
                email,
                phone,
                passwordEncoder.encode(request.password()),
                true);
        user.addRole(memberRole);
        user = userRepository.save(user);
        membershipRepository.save(new FamilyMembership(family, user, role));
        return toView(user, family);
    }

    @Transactional
    public String resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài khoản không tồn tại."));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return "Đã đặt mật khẩu mới cho tài khoản " + user.getUsername() + ".";
    }

    @Transactional(readOnly = true)
    public List<FamilySummary> listFamilies() {
        return familyRepository.findAllWithMemberCount().stream()
                .map(row -> {
                    Family family = (Family) row[0];
                    return new FamilySummary(family.getId(), family.getName(), (Long) row[1], family.getCreatedAt());
                })
                .toList();
    }

    private UserView toView(User user) {
        Family family = membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(user.getId())
                .map(FamilyMembership::getFamily)
                .orElse(null);
        return toView(user, family);
    }

    private UserView toView(User user, Family family) {
        return new UserView(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.isEmailVerified(),
                family == null ? null : family.getId(),
                family == null ? null : family.getName(),
                user.getRoles().stream().map(Role::getCode).sorted().toList());
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
