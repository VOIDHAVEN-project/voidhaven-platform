package cn.voidhaven.api.domain;

import org.junit.jupiter.api.Test;
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
}
