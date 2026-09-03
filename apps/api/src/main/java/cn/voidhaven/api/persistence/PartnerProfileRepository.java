package cn.voidhaven.api.persistence;
import cn.voidhaven.api.domain.PartnerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface PartnerProfileRepository extends JpaRepository<PartnerProfile, String> {
    Optional<PartnerProfile> findByAccountId(String accountId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartnerProfile p where p.id = :id")
    Optional<PartnerProfile> findByIdForUpdate(@Param("id") String id);
}
