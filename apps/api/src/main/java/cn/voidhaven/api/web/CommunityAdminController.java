package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.CommunityService;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.MediaService;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/community")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CommunityAdminController {
    record EntryRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                        @NotBlank @Pattern(regexp = "LINK|QR_CODE|TEXT") String presentationType,
                        @NotBlank @Pattern(regexp = "URL|EMAIL|WECHAT|QQ|PHONE|OTHER") String contactChannel,
                        @NotBlank @Pattern(regexp = "PUBLIC|COMMUNITY_GATED") String accessLevel,
                        @Size(max = 2048) String value, @Size(max = 26) String mediaAssetId,
                        boolean publicExposureConfirmed,
                        boolean primary, @Min(0) @Max(1000) int sortOrder) { }
    record InviteRequest(@NotBlank @Size(max = 120) String label, @Min(0) @Max(90) int expiresInDays,
                         @Min(0) @Max(10000) int maxUses) { }
    record Reason(@NotBlank @Size(max = 1000) String reason) { }

    private final CommunityService community;
    private final MediaService media;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;

    public CommunityAdminController(CommunityService community, MediaService media, IdempotencyService idempotency, AccountRepository accounts) {
        this.community = community;
        this.media = media;
        this.idempotency = idempotency;
        this.accounts = accounts;
    }

    @PostMapping(path = "/media", consumes = "multipart/form-data")
    ApiResponse<MediaService.Uploaded> uploadQr(@RequestHeader("Idempotency-Key") String key,
            @RequestParam @NotBlank @Size(max = 240) String altText, @RequestParam("file") MultipartFile file,
            Authentication auth, HttpServletRequest request) {
        community.requireOperational();
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (Exception error) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "MEDIA_REJECTED", "图片无法读取，请重新选择文件。"); }
        return ok(idempotency.execute(key, Map.of("altText", altText, "name", String.valueOf(file.getOriginalFilename()), "size", bytes.length),
                MediaService.Uploaded.class,
                () -> media.uploadCommunity(altText, bytes, accountId(auth), "SUPER_ADMIN", requestId(request))), request);
    }

    @GetMapping("/entries")
    ApiResponse<List<CommunityService.EntrySummary>> entries(HttpServletRequest request) { return ok(community.entries(), request); }

    @PostMapping("/entries")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    ApiResponse<CommunityService.EntrySummary> createEntry(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EntryRequest body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, body, CommunityService.EntrySummary.class,
                () -> community.createEntry(body.name(), body.description(), body.presentationType(), body.contactChannel(),
                        body.accessLevel(), body.value(), body.mediaAssetId(), body.publicExposureConfirmed(),
                        body.primary(), body.sortOrder(), accountId(auth), "SUPER_ADMIN", requestId(request))), request);
    }

    @PatchMapping("/entries/{id}")
    ApiResponse<CommunityService.EntrySummary> updateEntry(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("If-Match") String version, @Valid @RequestBody EntryRequest body, Authentication auth, HttpServletRequest request) {
        long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "body", body), CommunityService.EntrySummary.class,
                () -> community.updateEntry(id, expected, body.name(), body.description(), body.presentationType(),
                        body.contactChannel(), body.accessLevel(), body.value(), body.mediaAssetId(),
                        body.publicExposureConfirmed(), body.primary(), body.sortOrder(), accountId(auth),
                        "SUPER_ADMIN", requestId(request))), request);
    }

    @PostMapping("/entries/{id}/deactivate")
    ApiResponse<CommunityService.EntrySummary> deactivateEntry(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("If-Match") String version, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) {
        long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "reason", body.reason()), CommunityService.EntrySummary.class,
                () -> community.deactivateEntry(id, expected, body.reason(), accountId(auth), "SUPER_ADMIN", requestId(request))), request);
    }

    @GetMapping("/invite-batches")
    ApiResponse<List<CommunityService.InviteBatch>> invites(HttpServletRequest request) { return ok(community.inviteBatches(), request); }

    @PostMapping("/invite-batches")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    ApiResponse<CommunityService.CreatedInviteBatch> createInvite(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody InviteRequest body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.executeSingleDelivery(key, body,
                () -> community.createInviteBatch(body.label(), body.expiresInDays(), body.maxUses(), accountId(auth), "SUPER_ADMIN", requestId(request))), request);
    }

    @PostMapping("/invite-batches/{id}/revoke")
    ApiResponse<CommunityService.InviteBatch> revoke(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "reason", body.reason()), CommunityService.InviteBatch.class,
                () -> community.revokeInviteBatch(id, body.reason(), accountId(auth), "SUPER_ADMIN", requestId(request))), request);
    }

    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).orElseThrow().getId(); }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return new ApiResponse<>(data, requestId(request)); }
    private long version(String value) {
        try { return Long.parseLong(value.replace("\"", "")); }
        catch (NumberFormatException error) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_VERSION_REQUIRED", "请携带有效的社群入口版本后重试。"); }
    }
}
