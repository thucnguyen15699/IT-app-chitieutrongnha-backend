package vn.nhaminh.api.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.nhaminh.api.family.FamilyMembershipRepository;
import vn.nhaminh.api.notification.PushService.PushMessage;
import vn.nhaminh.api.transaction.TransactionCreatedEvent;
import vn.nhaminh.api.transaction.TransactionType;

/**
 * Có người thêm khoản thu/chi → báo cho các thành viên KHÁC trong gia đình.
 * Chạy sau khi giao dịch đã lưu xong (AFTER_COMMIT) và ở luồng riêng (@Async),
 * nên không làm chậm việc lưu và lỗi gửi thông báo không ảnh hưởng tới giao dịch.
 */
@Component
public class TransactionNotifier {
    private static final Logger log = LoggerFactory.getLogger(TransactionNotifier.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FamilyMembershipRepository membershipRepository;
    private final PushService pushService;

    public TransactionNotifier(FamilyMembershipRepository membershipRepository, PushService pushService) {
        this.membershipRepository = membershipRepository;
        this.pushService = pushService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCreated(TransactionCreatedEvent event) {
        try {
            // Người tự thêm thì không cần báo lại; khoản hệ thống tự ghi thì báo cho cả nhà.
            List<Long> recipients = membershipRepository.findMembers(event.familyId()).stream()
                    .map(membership -> membership.getUser().getId())
                    .filter(userId -> event.automatic() || !userId.equals(event.actorUserId()))
                    .toList();
            if (recipients.isEmpty()) return;
            var result = pushService.sendToUsers(recipients, toMessage(event));
            log.debug("Thông báo giao dịch #{}: gửi {} thiết bị, xóa {} thiết bị hết hạn",
                    event.transactionId(), result.sent(), result.removed());
        } catch (RuntimeException exception) {
            log.warn("Không gửi được thông báo cho giao dịch #{}: {}", event.transactionId(), exception.getMessage());
        }
    }

    static PushMessage toMessage(TransactionCreatedEvent event) {
        return toMessage(event, LocalDate.now(VIETNAM));
    }

    /**
     * today: "hôm nay" theo giờ Việt Nam (server có thể chạy UTC).
     * Giao dịch không phải hôm nay thì ghi rõ ngày, để người nhận biết cần xem tháng nào.
     * Bấm thông báo sẽ mở Tổng quan đúng ngày (và tháng) của giao dịch.
     */
    static PushMessage toMessage(TransactionCreatedEvent event, LocalDate today) {
        boolean expense = event.type() == TransactionType.EXPENSE;
        String title = event.automatic()
                ? "Đã tự ghi khoản chi đến hạn"
                : event.actorName() + (expense ? " vừa thêm khoản chi" : " vừa thêm khoản thu");
        StringBuilder body = new StringBuilder()
                .append(expense ? "-" : "+").append(formatMoney(event.amount()))
                .append(" · ").append(event.categoryName());
        LocalDate date = event.occurredOn();
        if (date != null && !date.equals(today)) {
            body.append(" · ngày ").append(date.format(date.getYear() == today.getYear() ? DAY_MONTH : DAY_MONTH_YEAR));
        }
        if (expense && event.targetName() != null) body.append(" · cho ").append(event.targetName());
        if (event.note() != null && !event.note().isBlank()) body.append(" — ").append(event.note());
        if (event.automatic()) body.append(" (do ").append(event.actorName()).append(" lên kế hoạch)");
        String url = date == null ? "/#/tong-quan" : "/#/tong-quan?ngay=" + date;
        return new PushMessage(title, body.toString(), url, "tx-" + event.transactionId());
    }

    static String formatMoney(BigDecimal amount) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return format.format(amount) + " ₫";
    }
}
