package vn.nhaminh.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vn.nhaminh.api.planning.PlannedExpenseAutoPayer;

/**
 * Khởi động TOÀN BỘ ứng dụng (database H2 trong bộ nhớ, không đụng MySQL thật).
 * Bắt các lỗi chỉ lộ ra lúc chạy thật như: thiếu bean, constructor mơ hồ, cấu hình sai,
 * entity không khớp — những lỗi mà test từng lớp riêng lẻ không thấy.
 */
@SpringBootTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:contexttest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "app.jwt.secret=test-secret-for-context-load-only-0123456789"
})
class ApplicationContextTest {
    @Autowired private ApplicationContext context;

    @Test
    void applicationStartsWithAllComponents() {
        assertNotNull(context.getBean(PlannedExpenseAutoPayer.class));
    }
}
