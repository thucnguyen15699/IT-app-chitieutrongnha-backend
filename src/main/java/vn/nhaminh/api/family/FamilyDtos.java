package vn.nhaminh.api.family;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class FamilyDtos {
    private FamilyDtos() {}

    public record FamilyNameRequest(@NotBlank @Size(max = 120) String name) {}

    public record MemberView(Long userId, String name, String username, String role, Instant joinedAt, boolean me) {}

    public record InvitationView(
            Long id, String email, String phoneNumber, String message, String invitedByName, Instant createdAt) {}

    /**
     * myRole: OWNER / MEMBER, hoặc ADMIN_VIEW khi quản trị viên đang xem gia đình khác (chỉ đọc).
     * canManage: được đổi tên gia đình (mọi thành viên; admin đang chỉ xem thì không).
     */
    public record FamilyView(
            Long id,
            String name,
            String myRole,
            boolean canManage,
            boolean canInvite,
            List<MemberView> members,
            List<InvitationView> pendingInvitations) {}

    /** Mời theo email, SĐT, hoặc cả hai (ít nhất một). */
    public record InviteRequest(
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phoneNumber,
            @Size(max = 255) String message) {}

    public record InviteResult(InvitationView invitation, boolean emailSent, String note) {}

    /** Lời mời người dùng hiện tại nhận được. */
    public record IncomingInvitationView(
            Long id,
            Long familyId,
            String familyName,
            String invitedByName,
            String message,
            Instant createdAt,
            String matchedBy,
            String currentFamilyName) {}

    public record MessageResponse(String message) {}

    /** Một gia đình người dùng đang tham gia (để chuyển qua lại). */
    public record FamilyOption(Long id, String name, String role, long memberCount) {}

    /** Kết quả đồng ý lời mời: id gia đình vừa vào. */
    public record AcceptResult(String message, Long familyId) {}
}
