package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
public class MediaController {
    private final MediaService media;
    private cn.voidhaven.api.service.AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(cn.voidhaven.api.service.AdminAuthorizationService authorization) { this.authorization = authorization; }
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    private final cn.voidhaven.api.service.MediaReadService reads;

    public MediaController(MediaService media, IdempotencyService idempotency, AccountRepository accounts, cn.voidhaven.api.service.MediaReadService reads) { this.media = media; this.idempotency = idempotency; this.accounts = accounts; this.reads = reads; }

    @GetMapping("/api/v1/media/{id}/preview")
    ResponseEntity<byte[]> preview(@PathVariable String id, Authentication auth) {
        String accountId = accountId(auth);
        boolean administrator = isAdministrator(auth);
        var stored = reads.loadManaged(id, accountId, administrator);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.mediaType())).cacheControl(CacheControl.noStore()).body(stored.bytes());
    }

    @PostMapping(path = "/api/v1/media/uploads", consumes = "multipart/form-data")
    ApiResponse<MediaService.Uploaded> upload(@RequestHeader("Idempotency-Key") String key, @RequestParam @NotBlank String contentItemId, @RequestParam @NotBlank @Size(max = 240) String altText, @RequestParam("file") MultipartFile file, Authentication auth, HttpServletRequest request) {
        String accountId = accountId(auth);
        boolean administrator = isAdministrator(auth);
        String role = auth.getAuthorities().stream().anyMatch(value -> "ROLE_SUPER_ADMIN".equals(value.getAuthority())) ? "SUPER_ADMIN" : administrator ? "ADMIN" : "PARTNER";
        if (administrator) authorization.requireContent("CONTENT_EDIT", contentItemId);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception error) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "MEDIA_REJECTED", "图片无法读取，请重新选择文件。 "); }
        MediaService.Uploaded uploaded = idempotency.execute(key, new UploadIntent(contentItemId, altText, file.getOriginalFilename(), sha256(bytes)), MediaService.Uploaded.class, () -> media.upload(contentItemId, altText, bytes, accountId, role, administrator, requestId(request)));
        return new ApiResponse<>(uploaded, requestId(request));
    }

    @PostMapping(path = "/api/v1/media/action-uploads", consumes = "multipart/form-data")
    ApiResponse<MediaService.Uploaded> uploadAction(@RequestHeader("Idempotency-Key") String key,
            @RequestParam @NotBlank String contentItemId, @RequestParam @NotBlank @Size(max = 240) String altText,
            @RequestParam("file") MultipartFile file, Authentication auth, HttpServletRequest request) {
        String accountId = accountId(auth);
        boolean administrator = isAdministrator(auth);
        String role = auth.getAuthorities().stream().anyMatch(value -> "ROLE_SUPER_ADMIN".equals(value.getAuthority())) ? "SUPER_ADMIN" : administrator ? "ADMIN" : "PARTNER";
        if (administrator) authorization.requireContent("CONTENT_EDIT", contentItemId);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception error) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "MEDIA_REJECTED", "图片无法读取，请重新选择文件。"); }
        MediaService.Uploaded uploaded = idempotency.execute(key,
                new UploadIntent(contentItemId, altText, file.getOriginalFilename(), sha256(bytes)),
                MediaService.Uploaded.class,
                () -> media.uploadAction(contentItemId, altText, bytes, accountId, role, administrator, requestId(request)));
        return new ApiResponse<>(uploaded, requestId(request));
    }

    record UploadIntent(String contentItemId, String altText, String originalFilename, String contentHash) { }
    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).filter(account -> account.isActive()).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号已停用或资格不可用。")).getId(); }
    private boolean isAdministrator(Authentication auth) { return auth.getAuthorities().stream().anyMatch(value -> "ROLE_ADMIN".equals(value.getAuthority()) || "ROLE_SUPER_ADMIN".equals(value.getAuthority())); }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (Exception error) { throw new IllegalStateException("Cannot hash media request", error); } }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
