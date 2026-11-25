package vn.nhaminh.api.admin;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.admin.AdminDtos.FamilySummary;

/** Danh sách gia đình trên hệ thống: để admin gán tài khoản mới hoặc chọn gia đình để xem. */
@RestController
@RequestMapping("/api/admin/families")
public class AdminFamilyController {
    private final AdminUserService adminUserService;

    public AdminFamilyController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<FamilySummary> listFamilies() {
        return adminUserService.listFamilies();
    }
}
