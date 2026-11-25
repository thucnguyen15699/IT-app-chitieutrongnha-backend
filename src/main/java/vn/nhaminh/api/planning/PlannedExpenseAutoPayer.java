package vn.nhaminh.api.planning;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Khoản sắp chi đến hạn → tự chuyển thành khoản đã chi.
 * - Chạy mỗi ngày lúc 00:05 giờ Việt Nam.
 * - Chạy thêm một lần khi ứng dụng khởi động, để bù những ngày server tắt.
 * Mỗi khoản xử lý trong giao dịch riêng; chạy lại nhiều lần cũng không ghi trùng.
 * Tắt bằng biến môi trường PLANNED_AUTOPAY_ENABLED=false.
 */
@Component
public class PlannedExpenseAutoPayer {
    private static final Logger log = LoggerFactory.getLogger(PlannedExpenseAutoPayer.class);
    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PlannedExpenseRepository plannedRepository;
    private final PlanningService planningService;
    private final boolean enabled;
    private final Clock clock;

    /** Constructor Spring dùng (có 2 constructor nên phải chỉ rõ bằng @Autowired). */
    @Autowired
    public PlannedExpenseAutoPayer(
            PlannedExpenseRepository plannedRepository,
            PlanningService planningService,
            @Value("${app.planned.auto-pay-enabled:true}") boolean enabled) {
        this(plannedRepository, planningService, enabled, Clock.system(VIETNAM));
    }

    PlannedExpenseAutoPayer(PlannedExpenseRepository plannedRepository, PlanningService planningService,
            boolean enabled, Clock clock) {
        this.plannedRepository = plannedRepository;
        this.planningService = planningService;
        this.enabled = enabled;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void daily() {
        run();
    }

    /** @return số khoản đã tự ghi */
    public synchronized int run() {
        if (!enabled) return 0;
        LocalDate today = LocalDate.now(clock);
        int paid = 0;
        for (Long id : plannedRepository.findDuePendingIds(today)) {
            try {
                if (planningService.autoPayDue(id, today)) paid++;
            } catch (RuntimeException exception) {
                // Vd. danh mục/đối tượng không còn hợp lệ: bỏ qua khoản này, các khoản khác vẫn chạy.
                log.warn("Không tự ghi được khoản sắp chi #{}: {}", id, exception.getMessage());
            }
        }
        if (paid > 0) log.info("Đã tự ghi {} khoản sắp chi đến hạn thành khoản đã chi.", paid);
        return paid;
    }
}
