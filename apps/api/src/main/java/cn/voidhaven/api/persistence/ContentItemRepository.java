package cn.voidhaven.api.persistence;
import cn.voidhaven.api.domain.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ContentItemRepository extends JpaRepository<ContentItem, String> {
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.kind = :kind and c.workflowStatus = 'PUBLISHED' and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' order by c.id desc")
    List<ContentItem> findPublicByKind(@Param("kind") ContentKind kind, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.id = :id and c.kind = :kind and c.workflowStatus = 'PUBLISHED' and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE'")
    Optional<ContentItem> findPublicByIdAndKind(@Param("id") String id, @Param("kind") ContentKind kind);
    @EntityGraph(attributePaths = "currentRevision")
    List<ContentItem> findByOwnerPartnerProfileIdOrderByIdDesc(String ownerPartnerProfileId);
    long countByOwnerPartnerProfileIdAndWorkflowStatusAndVisibilityAndRecordState(String profileId, WorkflowStatus status, Visibility visibility, RecordState recordState);
    List<ContentItem> findByWorkflowStatusOrderByIdAsc(WorkflowStatus status);
}
