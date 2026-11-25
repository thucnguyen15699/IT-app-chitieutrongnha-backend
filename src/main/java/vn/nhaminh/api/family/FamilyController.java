package vn.nhaminh.api.family;

import jakarta.validation.Valid;
import java.util.List;
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
import vn.nhaminh.api.family.FamilyDtos.FamilyNameRequest;
import vn.nhaminh.api.family.FamilyDtos.FamilyView;
import vn.nhaminh.api.family.FamilyDtos.IncomingInvitationView;
import vn.nhaminh.api.family.FamilyDtos.InviteRequest;
import vn.nhaminh.api.family.FamilyDtos.InviteResult;
import vn.nhaminh.api.family.FamilyDtos.MessageResponse;

@RestController
@RequestMapping("/api")
public class FamilyController {
    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @GetMapping("/family")
    public FamilyView myFamily(Authentication authentication) {
        return familyService.myFamily(authentication.getName());
    }

    @PostMapping("/family")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyView createFamily(Authentication authentication, @Valid @RequestBody FamilyNameRequest request) {
        return familyService.createFamily(authentication.getName(), request.name());
    }

    @PutMapping("/family")
    public FamilyView rename(Authentication authentication, @Valid @RequestBody FamilyNameRequest request) {
        return familyService.rename(authentication.getName(), request.name());
    }

    @PostMapping("/family/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResult invite(Authentication authentication, @Valid @RequestBody InviteRequest request) {
        return familyService.invite(authentication.getName(), request);
    }

    @DeleteMapping("/family/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(Authentication authentication, @PathVariable Long id) {
        familyService.cancelInvitation(authentication.getName(), id);
    }

    @GetMapping("/invitations/mine")
    public List<IncomingInvitationView> incoming(Authentication authentication) {
        return familyService.incoming(authentication.getName());
    }

    @PostMapping("/invitations/{id}/accept")
    public MessageResponse accept(Authentication authentication, @PathVariable Long id) {
        familyService.accept(authentication.getName(), id);
        return new MessageResponse("Bạn đã tham gia gia đình mới.");
    }

    @PostMapping("/invitations/{id}/decline")
    public MessageResponse decline(Authentication authentication, @PathVariable Long id) {
        familyService.decline(authentication.getName(), id);
        return new MessageResponse("Đã từ chối lời mời.");
    }
}
