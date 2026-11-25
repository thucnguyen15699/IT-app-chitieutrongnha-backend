package vn.nhaminh.api.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.notification.PushService.SendResult;
import vn.nhaminh.api.notification.PushService.SubscribeRequest;

@RestController
@RequestMapping("/api/push")
public class PushController {
    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    /** Khóa công khai VAPID: trình duyệt cần khi đăng ký nhận thông báo. */
    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", pushService.publicKey());
    }

    public record SubscriptionBody(
            @NotBlank @Size(max = 2048) String endpoint,
            @NotBlank @Size(max = 255) String p256dh,
            @NotBlank @Size(max = 64) String auth,
            @Size(max = 100) String deviceLabel) {}

    public record EndpointBody(@NotBlank @Size(max = 2048) String endpoint) {}

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(Authentication authentication, @Valid @RequestBody SubscriptionBody body) {
        pushService.subscribe(authentication.getName(),
                new SubscribeRequest(body.endpoint(), body.p256dh(), body.auth(), body.deviceLabel()));
    }

    @PostMapping("/subscriptions/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(Authentication authentication, @Valid @RequestBody EndpointBody body) {
        pushService.unsubscribe(authentication.getName(), body.endpoint());
    }

    @PostMapping("/test")
    public SendResult sendTest(Authentication authentication) {
        return pushService.sendTest(authentication.getName());
    }
}
