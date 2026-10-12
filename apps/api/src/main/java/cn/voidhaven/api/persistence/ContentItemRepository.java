package cn.voidhaven.api.persistence;
import cn.voidhaven.api.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ContentItemRepository extends JpaRepository<ContentItem, String> {
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.kind = :kind and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' order by c.id desc")
    List<ContentItem> findPublicByKind(@Param("kind") ContentKind kind, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c join c.publishedRevision r where c.kind = :kind and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and (:cursor is null or c.id < :cursor) and (:query is null or lower(r.title) like lower(concat('%', :query, '%')) or lower(r.summary) like lower(concat('%', :query, '%')) or lower(r.sourceName) like lower(concat('%', :query, '%'))) order by c.id desc")
    List<ContentItem> findPublicPageByKind(@Param("kind") ContentKind kind, @Param("cursor") String cursor, @Param("query") String query, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c join c.publishedRevision r where (:kind is null or c.kind = :kind) and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and (:cursor is null or c.id < :cursor) and (:query is null or lower(r.title) like lower(concat('%', :query, '%')) or lower(r.summary) like lower(concat('%', :query, '%')) or lower(r.sourceName) like lower(concat('%', :query, '%'))) order by c.id desc")
    List<ContentItem> findPublicSearch(@Param("kind") ContentKind kind, @Param("cursor") String cursor, @Param("query") String query, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.id = :id and c.kind = :kind and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE'")
    Optional<ContentItem> findPublicByIdAndKind(@Param("id") String id, @Param("kind") ContentKind kind);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c join c.publishedRevision r where c.moduleKey = :moduleKey and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and (:cursor is null or c.id < :cursor) and (:query is null or lower(r.title) like lower(concat('%', :query, '%')) or lower(r.summary) like lower(concat('%', :query, '%')) or lower(r.sourceName) like lower(concat('%', :query, '%'))) order by c.id desc")
    List<ContentItem> findPublicPageByModuleKey(@Param("moduleKey") String moduleKey, @Param("cursor") String cursor, @Param("query") String query, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.id in :ids and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' order by c.id desc")
    List<ContentItem> findPublicByIds(@Param("ids") Collection<String> ids);
    @EntityGraph(attributePaths = {"publishedRevision", "currentRevision"})
    @Query("select c from ContentItem c where c.id in :ids")
    List<ContentItem> findGraphByIds(@Param("ids") Collection<String> ids);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.id = :id and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE'")
    Optional<ContentItem> findPublicById(@Param("id") String id);
    @EntityGraph(attributePaths = "currentRevision")
    List<ContentItem> findByOwnerPartnerProfileIdOrderByIdDesc(String ownerPartnerProfileId);
    @EntityGraph(attributePaths = "currentRevision")
    List<ContentItem> findByWorkflowStatusOrderByIdAsc(WorkflowStatus status);
    @EntityGraph(attributePaths = "currentRevision")
    List<ContentItem> findAllByOrderByIdDesc();
    @EntityGraph(attributePaths = "currentRevision")
    @Query("select c from ContentItem c where c.id = :id")
    Optional<ContentItem> findWithCurrentRevisionById(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContentItem c where c.id = :id")
    Optional<ContentItem> findByIdForUpdate(@Param("id") String id);
    @Query("select count(c) from ContentItem c where c.ownerPartnerProfileId = :profileId and c.publishedRevision is not null and c.publicationStatus = 'ACTIVE' and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE'")
    long countPublicActiveByOwnerPartnerProfileId(@Param("profileId") String profileId);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.kind in ('COMPETITION', 'RECRUITMENT') and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and c.publicationStatus in ('ACTIVE', 'CLOSED')")
    List<ContentItem> findTimedPublicItems();
}
