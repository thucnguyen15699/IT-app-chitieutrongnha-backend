package vn.nhaminh.api.planning;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.planning.GoldDtos.GoldOverview;
import vn.nhaminh.api.planning.GoldDtos.GoldPriceRequest;
import vn.nhaminh.api.planning.GoldDtos.GoldPurchaseRequest;

/** Mọi thao tác trả về toàn bộ tổng quan vàng để giao diện cập nhật lãi/lỗ một lần. */
@RestController
@RequestMapping("/api/gold")
public class GoldController {
    private final GoldService goldService;

    public GoldController(GoldService goldService) {
        this.goldService = goldService;
    }

    @GetMapping
    public GoldOverview overview(Authentication authentication) {
        return goldService.overview(authentication.getName());
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public GoldOverview addPurchase(Authentication authentication, @Valid @RequestBody GoldPurchaseRequest request) {
        return goldService.addPurchase(authentication.getName(), request);
    }

    @PutMapping("/purchases/{id}")
    public GoldOverview updatePurchase(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody GoldPurchaseRequest request) {
        return goldService.updatePurchase(authentication.getName(), id, request);
    }

    @DeleteMapping("/purchases/{id}")
    public GoldOverview deletePurchase(Authentication authentication, @PathVariable Long id) {
        return goldService.deletePurchase(authentication.getName(), id);
    }

    @PutMapping("/price")
    public GoldOverview setPrice(Authentication authentication, @Valid @RequestBody GoldPriceRequest request) {
        return goldService.setPrice(authentication.getName(), request);
    }
}
