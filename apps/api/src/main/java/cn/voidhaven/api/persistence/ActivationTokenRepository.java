package cn.voidhaven.api.persistence;

import cn.voidhaven.api.domain.ActivationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from ActivationToken token where token.tokenHash = :hash")
    Optional<ActivationToken> findByTokenHashForUpdate(@Param("hash") String hash);
}
