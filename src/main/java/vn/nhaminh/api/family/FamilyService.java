package vn.nhaminh.api.family;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nhaminh.api.config.MailService;
import vn.nhaminh.api.family.FamilyDtos.FamilyView;
import vn.nhaminh.api.family.FamilyDtos.IncomingInvitationView;
import vn.nhaminh.api.family.FamilyDtos.InvitationView;
import vn.nhaminh.api.family.FamilyDtos.InviteRequest;
import vn.nhaminh.api.family.FamilyDtos.InviteResult;
import vn.nhaminh.api.family.FamilyDtos.MemberView;
import vn.nhaminh.api.user.PhoneNumbers;
import vn.nhaminh.api.user.User;

@Service
public class FamilyService {
    static final String OWNER = "OWNER";
    static final String MEMBER = "MEMBER";

    private final CurrentFamilyService currentFamily;
    private final FamilyRepository familyRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final FamilyInvitationRepository invitationRepository;
    private final MailService mailService;

    public FamilyService(
            CurrentFamilyService currentFamily,
            FamilyRepository familyRepository,
            FamilyMembershipRepository membershipRepository,
            FamilyInvitationRepository invitationRepository,
            MailService mailService) {
        this.currentFamily = currentFamily;
        this.familyRepository = familyRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.mailService = mailService;
    }

    // ================= Gia đình của tôi =================

    @Transactional(readOnly = true)
    public FamilyView myFamily(String username) {
        User user = currentFamily.findUser(username);
        Family family = currentFamily.findFamily(user); // admin có thể đang xem gia đình khác
        Optional<FamilyMembership> own = currentFamily.findOwnMembership(user)
                .filter(membership -> membership.getFamily().getId().equals(family.getId()));
        String myRole = own.map(FamilyMembership::getRole).orElse("ADMIN_VIEW");
        boolean isMember = own.isPresent();

        List<MemberView> members = membershipRepository.findMembers(family.getId()).stream()
                .map(m -> new MemberView(m.getUser().getId(), m.getUser().getName(), m.getUser().getUsername(),
                        m.getRole(), m.getJoinedAt(), m.getUser().getId().equals(user.getId())))
                .toList();
        List<InvitationView> pending = invitationRepository
                .findByFamily_IdAndStatusOrderByCreatedAtDesc(family.getId(), FamilyInvitation.Status.PENDING).stream()
                .map(FamilyService::toView)
                .toList();
        return new FamilyView(family.getId(), family.getName(), myRole, isMember, isMember, members, pending);
    }

    /** Người dùng chưa thuộc gia đình nào (vd. đăng ký để chờ lời mời) tự tạo gia đình. */
    @Transactional
    public FamilyView createFamily(String username, String name) {
        User user = currentFamily.findUser(username);
        if (currentFamily.findOwnFamily(user).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã thuộc một gia đình rồi.");
        }
        Family family = familyRepository.save(new Family(name.trim()));
        membershipRepository.save(new FamilyMembership(family, user, OWNER));
        return myFamily(username);
    }

    /** Mọi thành viên đều đổi được tên gia đình của chính mình (admin đang "chỉ xem" thì không). */
    @Transactional
    public FamilyView rename(String username, String name) {
        FamilyMembership membership = ownMembership(username);
        membership.getFamily().rename(name.trim());
        return myFamily(username);
    }

    // ================= Mời thành viên =================

    @Transactional
    public InviteResult invite(String username, InviteRequest request) {
        FamilyMembership membership = ownMembership(username);
        User inviter = membership.getUser();
        Family family = membership.getFamily();

        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (email == null && phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập email hoặc số điện thoại của người cần mời.");
        }
        if ((email != null && email.equalsIgnoreCase(inviter.getEmail()))
                || (phone != null && phone.equals(PhoneNumbers.normalizeOrNull(inviter.getPhoneNumber())))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn không thể tự mời chính mình.");
        }
        for (FamilyMembership member : membershipRepository.findMembers(family.getId())) {
            User existing = member.getUser();
            boolean sameEmail = email != null && email.equalsIgnoreCase(existing.getEmail());
            boolean samePhone = phone != null && phone.equals(PhoneNumbers.normalizeOrNull(existing.getPhoneNumber()));
            if (sameEmail || samePhone) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, existing.getName() + " đã ở trong gia đình rồi.");
            }
        }
        if ((email != null && invitationRepository.existsByFamily_IdAndEmailIgnoreCaseAndStatus(
                        family.getId(), email, FamilyInvitation.Status.PENDING))
                || (phone != null && invitationRepository.existsByFamily_IdAndPhoneNumberAndStatus(
                        family.getId(), phone, FamilyInvitation.Status.PENDING))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã có lời mời đang chờ cho email/số điện thoại này.");
        }

        String message = request.message() == null || request.message().isBlank() ? null : request.message().trim();
        FamilyInvitation invitation = invitationRepository.save(new FamilyInvitation(family, inviter, email, phone, message));

        boolean emailSent = email != null && mailService.trySend(email,
                inviter.getName() + " mời bạn vào gia đình \"" + family.getName() + "\" trên NhaMinh",
                invitationEmail(inviter, family, message));
        String note;
        if (email != null && !emailSent) {
            note = "Đã lưu lời mời nhưng chưa gửi được email. Người được mời vẫn thấy lời mời khi đăng nhập.";
        } else if (emailSent) {
            note = "Đã gửi email mời. Người được mời chỉ cần đăng nhập để đồng ý hoặc từ chối.";
        } else {
            note = "Đã tạo lời mời. Người có số điện thoại này sẽ thấy lời mời khi đăng nhập.";
        }
        return new InviteResult(toView(invitation), emailSent, note);
    }

    @Transactional
    public void cancelInvitation(String username, Long invitationId) {
        FamilyMembership membership = ownMembership(username);
        FamilyInvitation invitation = invitationRepository.findByIdAndFamily_Id(invitationId, membership.getFamily().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lời mời."));
        if (invitation.getStatus() != FamilyInvitation.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lời mời này đã được xử lý.");
        }
        invitation.cancel();
    }

    // ================= Lời mời tôi nhận được =================

    @Transactional(readOnly = true)
    public List<IncomingInvitationView> incoming(String username) {
        User user = currentFamily.findUser(username);
        Optional<Family> own = currentFamily.findOwnFamily(user);
        Long ownId = own.map(Family::getId).orElse(null);
        String ownName = own.map(Family::getName).orElse(null);
        List<IncomingInvitationView> result = new ArrayList<>();
        matchingInvitations(user).forEach((invitation, matchedBy) -> {
            if (Objects.equals(invitation.getFamily().getId(), ownId)) return; // đã ở gia đình đó
            result.add(new IncomingInvitationView(
                    invitation.getId(),
                    invitation.getFamily().getId(),
                    invitation.getFamily().getName(),
                    invitation.getInvitedBy().getName(),
                    invitation.getMessage(),
                    invitation.getCreatedAt(),
                    matchedBy,
                    ownName));
        });
        return result;
    }

    /**
     * Đồng ý: rời gia đình hiện tại (nếu có) và vào gia đình mời.
     * Nếu người rời đi là chủ và còn người ở lại, người vào sớm nhất được làm chủ.
     */
    @Transactional
    public void accept(String username, Long invitationId) {
        User user = currentFamily.findUser(username);
        FamilyInvitation invitation = findIncoming(user, invitationId);

        for (FamilyMembership old : membershipRepository.findByUserId(user.getId())) {
            Long oldFamilyId = old.getFamily().getId();
            boolean wasOwner = OWNER.equals(old.getRole());
            membershipRepository.delete(old);
            membershipRepository.flush();
            if (wasOwner) {
                List<FamilyMembership> remaining = membershipRepository.findMembers(oldFamilyId);
                if (!remaining.isEmpty() && remaining.stream().noneMatch(m -> OWNER.equals(m.getRole()))) {
                    remaining.getFirst().promoteToOwner();
                }
            }
        }
        membershipRepository.save(new FamilyMembership(invitation.getFamily(), user, MEMBER));
        invitation.respond(FamilyInvitation.Status.ACCEPTED, user);
    }

    @Transactional
    public void decline(String username, Long invitationId) {
        User user = currentFamily.findUser(username);
        findIncoming(user, invitationId).respond(FamilyInvitation.Status.DECLINED, user);
    }

    // ================= Hỗ trợ =================

    private FamilyMembership ownMembership(String username) {
        User user = currentFamily.findUser(username);
        return currentFamily.findOwnMembership(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bạn chưa thuộc gia đình nào."));
    }

    /** Lời mời đang chờ khớp email (đã xác minh) hoặc SĐT của người dùng; giá trị là cách khớp. */
    private Map<FamilyInvitation, String> matchingInvitations(User user) {
        Map<FamilyInvitation, String> matches = new LinkedHashMap<>();
        if (user.getEmail() != null && user.isEmailVerified()) {
            invitationRepository.findByEmailIgnoreCaseAndStatus(user.getEmail(), FamilyInvitation.Status.PENDING)
                    .forEach(invitation -> matches.put(invitation, "EMAIL"));
        }
        String phone = PhoneNumbers.normalizeOrNull(user.getPhoneNumber());
        if (phone != null) {
            invitationRepository.findByPhoneNumberAndStatus(phone, FamilyInvitation.Status.PENDING)
                    .forEach(invitation -> matches.putIfAbsent(invitation, "PHONE"));
        }
        return matches;
    }

    private FamilyInvitation findIncoming(User user, Long invitationId) {
        return matchingInvitations(user).keySet().stream()
                .filter(invitation -> invitation.getId().equals(invitationId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Lời mời không tồn tại, đã được xử lý, hoặc không dành cho bạn."));
    }

    private String invitationEmail(User inviter, Family family, String message) {
        return "Xin chào,\n\n"
                + inviter.getName() + " mời bạn tham gia gia đình \"" + family.getName()
                + "\" trên NhaMinh để cùng quản lý chi tiêu.\n"
                + (message == null ? "" : "\nLời nhắn: " + message + "\n")
                + "\n• Nếu bạn ĐÃ có tài khoản dùng email này: chỉ cần đăng nhập, lời mời sẽ hiện ra để bạn đồng ý hoặc từ chối.\n"
                + "• Nếu bạn CHƯA có tài khoản: hãy đăng ký bằng đúng email này (có thể bỏ trống tên gia đình), "
                + "xác minh email rồi đăng nhập để nhận lời mời.\n\n"
                + mailService.frontendBaseUrl() + "\n\n"
                + "Nếu bạn không biết người mời, hãy bỏ qua email này.";
    }

    private static InvitationView toView(FamilyInvitation invitation) {
        return new InvitationView(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getPhoneNumber(),
                invitation.getMessage(),
                invitation.getInvitedBy().getName(),
                invitation.getCreatedAt());
    }
}
