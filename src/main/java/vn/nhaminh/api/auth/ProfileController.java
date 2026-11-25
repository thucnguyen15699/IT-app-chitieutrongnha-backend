package vn.nhaminh.api.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.auth.AuthDtos.ProfileUpdateResponse;
import vn.nhaminh.api.auth.AuthDtos.UpdateProfileRequest;
import vn.nhaminh.api.auth.AuthDtos.VerificationResponse;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping
    public ProfileUpdateResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(authentication.getName(), request);
    }

    @PostMapping("/resend-verification")
    public VerificationResponse resendVerification(Authentication authentication) {
        return new VerificationResponse(profileService.resendVerification(authentication.getName()));
    }
}
