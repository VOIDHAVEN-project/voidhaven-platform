package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class MediaOrphanCleanupServiceTest {
    @Test void deletes_only_unattached_assets_from_private_storage_and_the_database() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        when(jdbc.query(startsWith("select ma.id"), org.mockito.ArgumentMatchers.<RowMapper<MediaOrphanCleanupService.Orphan>>any()))
                .thenReturn(List.of(new MediaOrphanCleanupService.Orphan("01JMEDIA00000000000000000", "content/revision/asset.jpg")));
        when(jdbc.query(startsWith("select storage_key"), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), eq("01JMEDIA00000000000000000"), eq("01JMEDIA00000000000000000")))
                .thenReturn("content/revision/asset.jpg");
        when(jdbc.update(startsWith("delete from media_assets"), eq("01JMEDIA00000000000000000")))
                .thenReturn(1);

        int removed = new MediaOrphanCleanupService(jdbc, storage).removeUnattachedAssets();

        assertThat(removed).isEqualTo(1);
        verify(storage).delete("content/revision/asset.jpg");
        verify(jdbc).update(startsWith("delete from media_assets"), eq("01JMEDIA00000000000000000"));
    }
}
