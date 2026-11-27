package vn.nhaminh.api;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NhaMinhApplication {
    /** Người dùng ở Việt Nam: "hôm nay", "tháng này" phải tính theo giờ VN dù server chạy UTC. */
    public static final String APP_TIME_ZONE = "Asia/Ho_Chi_Minh";

    public static void main(String[] args) {
        // Server (Termux/Docker) thường mặc định UTC → từ 0h–7h sáng LocalDate.now() vẫn là hôm qua:
        // chặn nhầm giao dịch "hôm nay", tự trả khoản sắp chi lệch một ngày.
        // Cột thời điểm (Instant) vẫn lưu theo UTC như cũ nên dữ liệu hiện có không bị ảnh hưởng.
        TimeZone.setDefault(TimeZone.getTimeZone(APP_TIME_ZONE));
        SpringApplication.run(NhaMinhApplication.class, args);
    }
}
