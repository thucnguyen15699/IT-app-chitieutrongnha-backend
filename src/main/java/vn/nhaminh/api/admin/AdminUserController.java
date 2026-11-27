package vn.nhaminh.api.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.admin.AdminDtos.CreateUserRequest;
import vn.nhaminh.api.admin.AdminDtos.ResetPasswordRequest;
import vn.nhaminh.api.admin.AdminDtos.UserView;
import vn.nhaminh.api.auth.AuthDtos.VerificationResponse;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<UserView> listUsers() {
        return adminUserService.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUserRequest request) {
        return adminUserService.createUser(request);
    }

    /** Đặt lại mật khẩu cho tài khoản khác. Chỉ ADMIN gọi được (SecurityConfig chặn /api/admin/**). */
    @PutMapping("/{id}/password")
    public VerificationResponse resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return new VerificationResponse(adminUserService.resetPassword(id, request.newPassword()));
    }
}
