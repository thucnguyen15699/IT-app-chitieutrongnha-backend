package vn.nhaminh.api.transaction;

import java.math.BigDecimal;

/**
 * Phát ra khi có khoản thu/chi mới (kể cả khi bấm "Đã chi" ở khoản sắp chi).
 * automatic = true: hệ thống tự ghi vì khoản sắp chi đã đến hạn (actor là người lập khoản đó).
 */
public record TransactionCreatedEvent(
        Long transactionId,
        Long familyId,
        Long actorUserId,
        String actorName,
        TransactionType type,
        BigDecimal amount,
        String categoryName,
        String targetName,
        String note,
        boolean automatic) {}
