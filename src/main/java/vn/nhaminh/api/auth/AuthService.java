package vn.nhaminh.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.auth.AuthDtos.AuthResponse;
import vn.nhaminh.api.auth.AuthDtos.LoginRequest;
import vn.nhaminh.api.auth.AuthDtos.RegistrationResponse;
import vn.nhaminh.api.auth.AuthDtos.RegisterRequest;
import vn.nhaminh.api.auth.AuthDtos.UserProfile;
import vn.nhaminh.api.auth.AuthDtos.VerificationResponse;
import vn.nhaminh.api.family.Family;
import vn.nhaminh.api.family.FamilyMembership;
import vn.nhaminh.api.family.FamilyMembershipRepository;
import vn.nhaminh.api.family.FamilyRepository;
import vn.nhaminh.api.user.PhoneNumbers;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RoleRepository roleRepository;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            UserRepository userRepository,
            FamilyRepository familyRepository,
            FamilyMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RoleRepository roleRepository,
            EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.familyRepository = familyRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.roleRepository = roleRepository;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập này đã được sử dụng.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được sử dụng.");
        }
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (phone != null && userRepository.existsByPhoneNumber(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại này đã được sử dụng.");
        }

        Role ownerRole = roleRepository.findByCode("OWNER")
                .orElseThrow(() -> new IllegalStateException("Role OWNER chưa được khởi tạo."));
        User user = new User(
                username,
                request.name().trim(),
                email,
                phone,
                passwordEncoder.encode(request.password()),
                false);
        user.addRole(ownerRole);
        user = userRepository.save(user);
        // Không đặt tên gia đình = người dùng chờ lời mời; sau khi đăng nhập có thể nhận lời mời hoặc tự tạo gia đình.
        String familyName = normalizeOptional(request.familyName());
        if (familyName != null) {
            Family family = familyRepository.save(new Family(familyName));
            membershipRepository.save(new FamilyMembership(family, user, "OWNER"));
        }
        emailVerificationService.issue(user);
        return new RegistrationResponse("Đã gửi liên kết xác minh đến email của bạn.");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(User::isEnabled)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu chưa chính xác."));
        if (user.getEmail() != null && !user.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Vui lòng xác minh email trước khi đăng nhập.");
        }
        Family family = findFamily(user);
        return createAuthResponse(user, family);
    }

    @Transactional(readOnly = true)
    public UserProfile currentUser(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn tồn tại."));
        Family family = findFamily(user);
        return toProfile(user, family);
    }

    public VerificationResponse verifyEmail(String token) {
        emailVerificationService.verify(token);
        return new VerificationResponse("Email đã được xác minh. Bạn có thể đăng nhập.");
    }

    /** Gia đình của người dùng, hoặc null nếu họ chưa thuộc gia đình nào (đang chờ lời mời). */
    private Family findFamily(User user) {
        return membershipRepository.findFirstByUserIdOrderByJoinedAtAsc(user.getId())
                .map(FamilyMembership::getFamily)
                .orElse(null);
    }

    private AuthResponse createAuthResponse(User user, Family family) {
        return new AuthResponse(jwtService.generateToken(user.getUsername()), toProfile(user, family));
    }

    private UserProfile toProfile(User user, Family family) {
        return new UserProfile(
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
        return value == null || value.isBlank() ? null : value.trim();
    }
}
