package com.territorial.admin.domain.admin.repository;

import com.territorial.admin.domain.admin.entity.AdminAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByEmail(String email);
}
