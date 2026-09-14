package cn.voidhaven.api.persistence;

import cn.voidhaven.api.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByLoginId(String loginId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select account from Account account where account.id = :id")
    Optional<Account> findByIdForShare(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") String id);
}
