package vn.nhaminh.api.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    List<PushSubscription> findByUser_IdIn(Collection<Long> userIds);

    long countByUser_Id(Long userId);
}
