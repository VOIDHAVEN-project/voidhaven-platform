package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.PublicationStatus;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

public final class HomepageEligibility {
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final Set<String> INACTIVE_OPERATIONAL = Set.of("PAUSED", "FILLED", "EXPIRED", "CANCELLED");
    public static final Set<String> SLOTS = Set.of("FOCUS", "COMPETITION", "RECRUITMENT", "RESOURCE");

    private HomepageEligibility() { }

    public static boolean kindMatchesSlot(String slot, ContentKind kind) {
        if (kind == null || slot == null) return false;
        return switch (slot) {
            case "FOCUS" -> true;
            case "COMPETITION" -> kind == ContentKind.COMPETITION;
            case "RECRUITMENT" -> kind == ContentKind.RECRUITMENT;
            case "RESOURCE" -> kind == ContentKind.RESOURCE;
            default -> false;
        };
    }

    public static ContentKind typedKind(String slot) {
        return switch (slot) {
            case "COMPETITION" -> ContentKind.COMPETITION;
            case "RECRUITMENT" -> ContentKind.RECRUITMENT;
            case "RESOURCE" -> ContentKind.RESOURCE;
            default -> null;
        };
    }

    public static boolean inDisplayWindow(Instant startsAt, Instant endsAt, Instant now) {
        if (startsAt != null && now.isBefore(startsAt)) return false;
        return endsAt == null || now.isBefore(endsAt);
    }

    public static boolean inactiveOperationalStatus(String operationalStatus) {
        return operationalStatus != null && INACTIVE_OPERATIONAL.contains(operationalStatus);
    }

    public static boolean deadlinePassed(ContentKind kind, JsonNode details, Instant now) {
        if (kind == ContentKind.RESOURCE || details == null || details.isNull()) return false;
        String deadlineAt = text(details, "deadlineAt");
        if (deadlineAt != null) {
            try {
                return Instant.parse(deadlineAt).isBefore(now);
            } catch (Exception ignored) {
                return false;
            }
        }
        String deadlineDate = text(details, "deadlineDate");
        if (deadlineDate == null) return false;
        try {
            return LocalDate.parse(deadlineDate).isBefore(now.atZone(SHANGHAI).toLocalDate());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String deadlineLabel(JsonNode details) {
        if (details == null || details.isNull()) return null;
        String deadlineAt = text(details, "deadlineAt");
        if (deadlineAt != null) return deadlineAt;
        return text(details, "deadlineDate");
    }

    public static boolean publiclyCuratable(
            Visibility visibility, RecordState recordState, boolean published, boolean hasPublicSource) {
        return visibility == Visibility.PUBLIC
                && recordState == RecordState.ACTIVE
                && published
                && hasPublicSource;
    }

    public static boolean currentlyListable(
            Visibility visibility,
            RecordState recordState,
            PublicationStatus publicationStatus,
            boolean published,
            boolean hasPublicSource,
            ContentKind kind,
            String operationalStatus,
            JsonNode details,
            Instant now) {
        if (!publiclyCuratable(visibility, recordState, published, hasPublicSource)) return false;
        if (publicationStatus == PublicationStatus.CLOSED) return false;
        if (inactiveOperationalStatus(operationalStatus)) return false;
        return !deadlinePassed(kind, details, now);
    }

    public static String issue(
            Visibility visibility,
            RecordState recordState,
            PublicationStatus publicationStatus,
            boolean published,
            boolean hasPublicSource,
            boolean kindMatches,
            Instant startsAt,
            Instant endsAt,
            ContentKind kind,
            String operationalStatus,
            JsonNode details,
            Instant now) {
        if (recordState != RecordState.ACTIVE) return "已归档或已删除，无法继续公开。";
        if (!published) return "尚未发布，不能出现在首页。";
        if (visibility != Visibility.PUBLIC) return "当前不可见，已从公开首页退出。";
        if (!hasPublicSource) return "来源不再允许公开。";
        if (!kindMatches) return "内容类型与栏目不匹配。";
        if (publicationStatus == PublicationStatus.CLOSED) return "已关闭，不再作为有效公开项。";
        if (inactiveOperationalStatus(operationalStatus)) return "当前状态为" + operationalStatus + "，已自动退出展示。";
        if (deadlinePassed(kind, details, now)) return "已过截止时间，已自动退出展示。";
        if (!inDisplayWindow(startsAt, endsAt, now)) return "不在设定的展示时间范围内。";
        return null;
    }

    private static String text(JsonNode details, String field) {
        JsonNode value = details.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
