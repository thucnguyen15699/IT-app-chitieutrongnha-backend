package vn.nhaminh.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 30) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72) String password,
            /** Bỏ trống nếu người dùng đang chờ lời mời vào gia đình khác. */
            @Size(max = 120) String familyName) {}

    public record LoginRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 72) String password) {}

    public record UserProfile(
            Long id,
            String name,
            String username,
            String email,
            String phoneNumber,
            boolean emailVerified,
            Long familyId,
            String familyName,
            List<String> roles) {}
    public record AuthResponse(String token, UserProfile user) {}
    public record RegistrationResponse(String message) {}
    public record VerificationResponse(String message) {}
    public record ErrorResponse(String message) {}
}
