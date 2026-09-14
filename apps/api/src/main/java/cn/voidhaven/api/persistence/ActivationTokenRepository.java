package cn.voidhaven.api.persistence;

import cn.voidhaven.api.domain.ActivationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, String> {
    Optional<ActivationToken> findByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from ActivationToken token where token.tokenHash = :hash")
    Optional<ActivationToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update ActivationToken token set token.consumedAt = :now where token.accountId = :accountId and token.consumedAt is null")
    int invalidateUnused(@Param("accountId") String accountId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update ActivationToken token set token.consumedAt = :now where token.accountId = :accountId and token.consumedAt is null and token.id <> :keepId")
    int invalidateUnusedExcept(@Param("accountId") String accountId, @Param("keepId") String keepId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update ActivationToken token set token.consumedAt = :now where token.accountId = :accountId and token.tokenType = :tokenType and token.consumedAt is null")
    int invalidateUnusedByType(@Param("accountId") String accountId, @Param("tokenType") String tokenType, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update ActivationToken token set token.consumedAt = :now where token.accountId = :accountId and token.tokenType = :tokenType and token.consumedAt is null and token.id <> :keepId")
    int invalidateUnusedByTypeExcept(@Param("accountId") String accountId, @Param("tokenType") String tokenType, @Param("keepId") String keepId, @Param("now") Instant now);
}
