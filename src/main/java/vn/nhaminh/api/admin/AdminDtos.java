package vn.nhaminh.api.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    /**
     * Gán vào gia đình có sẵn (familyId) HOẶC tạo gia đình mới (familyName) — chọn đúng một.
     */
    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}") String username,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72) String password,
            Long familyId,
            @Size(max = 120) String familyName) {}

    public record UserView(
            Long id,
            String name,
            String username,
            String email,
            String phoneNumber,
            boolean emailVerified,
            Long familyId,
            String familyName,
            List<String> roles) {}

    public record FamilySummary(Long id, String name, long memberCount, Instant createdAt) {}
}
