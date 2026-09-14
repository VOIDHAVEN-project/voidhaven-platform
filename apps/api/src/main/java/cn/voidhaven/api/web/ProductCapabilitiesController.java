package cn.voidhaven.api.web;

import cn.voidhaven.api.service.CommunityService;
import cn.voidhaven.api.service.PrivateValueCipher;
import cn.voidhaven.api.service.PasswordSelfServiceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class ProductCapabilitiesController {
    public record ProductCapabilities(boolean privateContactEnabled, boolean communityGateEnabled,
                                      boolean gatedRecruitmentEnabled, boolean statusQueryEnabled,
                                      boolean passwordSelfServiceEnabled) { }

    private final PrivateValueCipher privateValues;
    private final CommunityService community;
    private final PasswordSelfServiceService passwordRecovery;

    public ProductCapabilitiesController(PrivateValueCipher privateValues, CommunityService community,
                                         PasswordSelfServiceService passwordRecovery) {
        this.privateValues = privateValues;
        this.community = community;
        this.passwordRecovery = passwordRecovery;
    }

    @GetMapping("/capabilities")
    ApiResponse<ProductCapabilities> capabilities(HttpServletRequest request) {
        boolean privateContactEnabled = privateValues.isContactCollectionOperational();
        boolean communityGateEnabled = community.isOperational();
        return new ApiResponse<>(new ProductCapabilities(privateContactEnabled, communityGateEnabled,
                communityGateEnabled, true, passwordRecovery.isOperational()),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
