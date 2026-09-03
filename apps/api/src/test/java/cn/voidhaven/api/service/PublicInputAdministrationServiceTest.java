package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicInputAdministrationServiceTest {
    @Test void maps_only_the_approved_public_input_review_decisions() {
        assertThat(PublicInputAdministrationService.targetState("APPROVE")).isEqualTo("APPROVED");
        assertThat(PublicInputAdministrationService.targetState("CHANGES_REQUESTED")).isEqualTo("CHANGES_REQUESTED");
        assertThat(PublicInputAdministrationService.targetState("REJECT")).isEqualTo("REJECTED");
        assertThatThrownBy(() -> PublicInputAdministrationService.targetState("PUBLISH"))
                .hasMessageContaining("审核决定无效");
    }
}
