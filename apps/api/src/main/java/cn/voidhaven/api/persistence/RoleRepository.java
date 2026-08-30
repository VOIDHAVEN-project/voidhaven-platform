package cn.voidhaven.api.persistence;
import cn.voidhaven.api.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RoleRepository extends JpaRepository<Role, Long> { Optional<Role> findByCode(String code); }
