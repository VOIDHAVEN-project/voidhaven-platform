package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaOrphanCleanupServiceTest {
    @Test void queues_before_deleting_from_private_storage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        when(jdbc.query(startsWith("select ma.id"), org.mockito.ArgumentMatchers.<RowMapper<MediaOrphanCleanupService.Orphan>>any()))
                .thenReturn(List.of(new MediaOrphanCleanupService.Orphan("01JMEDIA00000000000000000", "content/revision/asset.jpg")));
        when(jdbc.update(startsWith("update media_assets set lifecycle_state = 'DELETION_PENDING'"), eq("01JMEDIA00000000000000000"), eq("01JMEDIA00000000000000000"), eq("01JMEDIA00000000000000000"), eq("01JMEDIA00000000000000000"), eq("01JMEDIA00000000000000000"))).thenReturn(1);
        when(jdbc.update(contains("locked_by = ?"), startsWith("instance-a:"))).thenReturn(1);
        when(jdbc.query(startsWith("select id, media_asset_id"), org.mockito.ArgumentMatchers.<RowMapper<MediaOrphanCleanupService.DeletionJob>>any(), startsWith("instance-a:")))
                .thenReturn(List.of(new MediaOrphanCleanupService.DeletionJob("01JJOB0000000000000000000", "01JMEDIA00000000000000000", "content/revision/asset.jpg")));
        when(jdbc.update(startsWith("update media_deletion_jobs set state = 'SUCCEEDED'"), eq("01JJOB0000000000000000000"), startsWith("instance-a:"))).thenReturn(1);

        int removed = new MediaOrphanCleanupService(jdbc, storage, "instance-a").removeUnattachedAssets();

        assertThat(removed).isEqualTo(1);
        verify(jdbc).update(startsWith("insert into media_deletion_jobs"), any(), eq("01JMEDIA00000000000000000"), eq("content/revision/asset.jpg"));
        verify(storage).delete("content/revision/asset.jpg");
        verify(jdbc).update(startsWith("update media_assets set lifecycle_state = 'DELETED'"), eq("01JMEDIA00000000000000000"));
    }

    @Test void failed_storage_deletion_stays_retryable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        when(jdbc.update(contains("locked_by = ?"), startsWith("instance-a:"))).thenReturn(1);
        when(jdbc.query(startsWith("select id, media_asset_id"), org.mockito.ArgumentMatchers.<RowMapper<MediaOrphanCleanupService.DeletionJob>>any(), startsWith("instance-a:")))
                .thenReturn(List.of(new MediaOrphanCleanupService.DeletionJob("01JJOB0000000000000000000", "01JMEDIA00000000000000000", "content/revision/asset.jpg")));
        doThrow(new IllegalStateException("storage unavailable")).when(storage).delete(anyString());

        int removed = new MediaOrphanCleanupService(jdbc, storage, "instance-a").processDueDeletions();

        assertThat(removed).isZero();
        verify(jdbc).update(startsWith("update media_deletion_jobs set state = 'FAILED'"), eq("IllegalStateException"), eq("01JJOB0000000000000000000"), startsWith("instance-a:"));
    }

    @Test void each_cleanup_invocation_uses_a_distinct_claim_id() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        when(jdbc.query(startsWith("select id, media_asset_id"), org.mockito.ArgumentMatchers.<RowMapper<MediaOrphanCleanupService.DeletionJob>>any(), anyString()))
                .thenReturn(List.of());
        MediaOrphanCleanupService service = new MediaOrphanCleanupService(jdbc, storage, "instance-a");

        service.processDueDeletions();
        service.processDueDeletions();

        org.mockito.ArgumentCaptor<String> claims = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(contains("set locked_by = ?"), claims.capture());
        assertThat(claims.getAllValues()).allMatch(value -> value.startsWith("instance-a:"));
        assertThat(claims.getAllValues()).doesNotHaveDuplicates();
    }
}
