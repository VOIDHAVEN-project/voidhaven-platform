package cn.voidhaven.api.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ContentItemStateTest {
    @Test void publishing_makes_an_approved_item_public_and_active() {
        ContentItem item = new ContentItem("01JLOCALTEST00000000000000", ContentKind.COMPETITION, WorkflowStatus.APPROVED, Visibility.HIDDEN, RecordState.ACTIVE);
        item.publish();
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.PUBLISHED);
        assertThat(item.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(item.getRecordState()).isEqualTo(RecordState.ACTIVE);
    }
    @Test void archiving_hides_the_public_projection() {
        ContentItem item = new ContentItem("01JLOCALTEST00000000000000", ContentKind.RESOURCE, WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        item.archive();
        assertThat(item.getVisibility()).isEqualTo(Visibility.HIDDEN);
        assertThat(item.getRecordState()).isEqualTo(RecordState.ARCHIVED);
    }
    @Test void closing_a_published_item_keeps_its_candidate_workflow_intact() {
        ContentItem item = new ContentItem("01JLOCALTEST00000000000000", ContentKind.RECRUITMENT, WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        item.publish();
        item.beginCandidateRevision();
        Instant closedAt = Instant.parse("2026-08-31T00:00:00Z");
        item.closePublic(closedAt);
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
        assertThat(item.getPublicationStatus()).isEqualTo(PublicationStatus.CLOSED);
        assertThat(item.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(item.getRecordState()).isEqualTo(RecordState.ACTIVE);
        assertThat(item.getClosedAt()).isEqualTo(closedAt);
    }
    @Test void restoring_an_archived_item_requires_a_new_publish_decision() {
        ContentItem item = new ContentItem("01JLOCALTEST00000000000000", ContentKind.RESOURCE, WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        item.archive();
        item.restore();
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
        assertThat(item.getPublicationStatus()).isEqualTo(PublicationStatus.NONE);
        assertThat(item.getVisibility()).isEqualTo(Visibility.HIDDEN);
        assertThat(item.getRecordState()).isEqualTo(RecordState.ACTIVE);
    }
}
