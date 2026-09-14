package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicInputAdministrationServiceTest {
    @Test void treats_legacy_pending_review_filter_as_received() {
        assertThat(PublicCaseStates.normalizeSubmissionFilter(null)).isEqualTo("RECEIVED");
        assertThat(PublicCaseStates.normalizeSubmissionFilter("PENDING_REVIEW")).isEqualTo("RECEIVED");
        assertThat(PublicCaseStates.normalizePartnerFilter("PENDING_REVIEW")).isEqualTo("RECEIVED");
        assertThat(PublicCaseStates.normalizeSubmissionFilter("ALL")).isNull();
    }

    @Test void maps_only_the_approved_public_input_review_decisions() {
        assertThat(PublicInputAdministrationService.targetState("APPROVE")).isEqualTo("RESOLVED");
        assertThat(PublicInputAdministrationService.targetState("REJECT")).isEqualTo("REJECTED");
        assertThatThrownBy(() -> PublicInputAdministrationService.targetState("CHANGES_REQUESTED"))
                .hasMessageContaining("审核决定无效");
        assertThatThrownBy(() -> PublicInputAdministrationService.targetState("PUBLISH"))
                .hasMessageContaining("审核决定无效");
    }

    @Test void rejects_a_second_decision_after_another_admin_has_already_reviewed_the_submission() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        when(jdbc.update(contains("and state in"), any(), any(), any(), any(), any())).thenReturn(0);
        PublicInputAdministrationService service = new PublicInputAdministrationService(jdbc, audit);

        assertThatThrownBy(() -> service.decideSubmission("submission-a", "APPROVE", "通过初审",
                "actor-a", "ADMIN", "request-a"))
                .hasFieldOrPropertyWithValue("code", "SUBMISSION_NOT_ACTIONABLE");
        verifyNoInteractions(audit);
    }
}
