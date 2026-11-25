package vn.nhaminh.api.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.auth.AuthDtos.AuthResponse;
import vn.nhaminh.api.auth.AuthDtos.LoginRequest;
import vn.nhaminh.api.auth.AuthDtos.RegistrationResponse;
import vn.nhaminh.api.auth.AuthDtos.RegisterRequest;
import vn.nhaminh.api.auth.AuthDtos.UserProfile;
import vn.nhaminh.api.auth.AuthDtos.VerificationResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/verify")
    public VerificationResponse verifyEmail(@RequestParam String token) {
        return authService.verifyEmail(token);
    }

    @GetMapping("/me")
    public UserProfile currentUser(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }
}
