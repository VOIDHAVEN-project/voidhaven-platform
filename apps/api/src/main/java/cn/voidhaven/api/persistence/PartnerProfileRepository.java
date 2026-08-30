package cn.voidhaven.api.persistence;
import cn.voidhaven.api.domain.PartnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PartnerProfileRepository extends JpaRepository<PartnerProfile, String> { Optional<PartnerProfile> findByAccountId(String accountId); }
