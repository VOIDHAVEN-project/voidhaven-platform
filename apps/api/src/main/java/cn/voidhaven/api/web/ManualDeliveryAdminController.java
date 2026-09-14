package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.ManualDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/manual-deliveries")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ManualDeliveryAdminController {
    record DeliveryRequest(
            @NotBlank @Pattern(regexp = "ACCOUNT|PARTNER_APPLICATION|SUBMISSION") String subjectType,
            @NotBlank @Size(min = 26, max = 26) String subjectId,
            @NotBlank @Pattern(regexp = "ACTIVATION|PASSWORD_RESET|STATUS_RESULT") String deliveryType,
            @Size(max = 1000) String note) { }

    private final ManualDeliveryService deliveries;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;

    public ManualDeliveryAdminController(ManualDeliveryService deliveries, IdempotencyService idempotency,
                                         AccountRepository accounts) {
        this.deliveries = deliveries;
        this.idempotency = idempotency;
        this.accounts = accounts;
    }

    @GetMapping
    ApiResponse<List<ManualDeliveryService.DeliveryRecord>> list(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            HttpServletRequest request) {
        return new ApiResponse<>(deliveries.list(subjectType, subjectId), requestId(request));
    }

    @PostMapping
    ApiResponse<ManualDeliveryService.DeliveryRecord> record(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DeliveryRequest body, Authentication auth, HttpServletRequest request) {
        return new ApiResponse<>(idempotency.execute(key, Map.of(
                "subjectType", body.subjectType(), "subjectId", body.subjectId(),
                "deliveryType", body.deliveryType(), "note", body.note() == null ? "" : body.note()),
                ManualDeliveryService.DeliveryRecord.class,
                () -> deliveries.record(body.subjectType(), body.subjectId(), body.deliveryType(),
                        accountId(auth), body.note(), "SUPER_ADMIN", requestId(request))), requestId(request));
    }

    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).orElseThrow().getId(); }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
