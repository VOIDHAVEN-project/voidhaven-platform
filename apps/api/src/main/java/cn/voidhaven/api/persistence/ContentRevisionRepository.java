package cn.voidhaven.api.persistence;
import cn.voidhaven.api.domain.ContentRevision;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ContentRevisionRepository extends JpaRepository<ContentRevision, String> { }
