package cn.voidhaven.api.web;

import cn.voidhaven.api.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
public class CommunityController {
    record VerifyRequest(@NotBlank @Size(min = 8, max = 256) String inviteCode) { }
    private final CommunityService community;
    private final ClientNetworkSubject networkSubject;

    public CommunityController(CommunityService community, ClientNetworkSubject networkSubject) {
        this.community = community;
        this.networkSubject = networkSubject;
    }

    @PostMapping("/community-gate/verify")
    ApiResponse<CommunityService.Grant> verify(@Valid @RequestBody VerifyRequest body, HttpServletRequest request) {
        return ok(community.verify(body.inviteCode(), networkSubject.resolve(request)), request);
    }

    @GetMapping("/community/entries")
    ApiResponse<List<CommunityService.Entry>> entries(@RequestHeader("X-Community-Grant") String grant, HttpServletRequest request) {
        return ok(community.publicEntries(grant), request);
    }

    private <T> ApiResponse<T> ok(T value, HttpServletRequest request) {
        return new ApiResponse<>(value, (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
