package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Set;

/** Public-case states are independent from content-review {@code PENDING_REVIEW}. */
public final class PublicCaseStates {
    public static final String RECEIVED = "RECEIVED";
    public static final String LEGACY_PENDING_REVIEW = "PENDING_REVIEW";

    public static final Set<String> SUBMISSION = Set.of(
            RECEIVED, "IN_PROGRESS", "WAITING_FOR_REQUESTER", "RESOLVED", "REJECTED", "CLOSED");
    public static final Set<String> PARTNER = Set.of(
            RECEIVED, "IN_REVIEW", "WAITING_FOR_APPLICANT", "APPROVED", "ACTIVATION_PENDING", "ACTIVATED", "REJECTED", "CLOSED");

    private PublicCaseStates() { }

    public static String normalizeSubmissionFilter(String status) {
        return normalize(status, SUBMISSION, "SUBMISSION_STATUS_INVALID", "公开输入状态筛选无效。");
    }

    public static String normalizePartnerFilter(String status) {
        return normalize(status, PARTNER, "PARTNER_APPLICATION_STATUS_INVALID", "合作方申请状态筛选无效。");
    }

    private static String normalize(String status, Set<String> allowed, String code, String message) {
        String normalized = status == null || status.isBlank() ? RECEIVED : status.trim();
        if ("ALL".equals(normalized)) return null;
        if (LEGACY_PENDING_REVIEW.equals(normalized)) return RECEIVED;
        if (!allowed.contains(normalized)) throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        return normalized;
    }
}
