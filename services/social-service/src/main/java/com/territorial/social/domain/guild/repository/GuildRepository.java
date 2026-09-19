package com.territorial.social.domain.guild.repository;

import com.territorial.social.domain.guild.entity.Guild;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuildRepository extends JpaRepository<Guild, Long> {

    boolean existsByName(String name);

    Page<Guild> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // 정원 체크-후-승인 직렬화용 비관적 락. 동시 승인이 같은 길드 행에서 대기 →
    // 두 번째 승인은 첫 승인 커밋 후 갱신된 인원수를 보고 정원 초과를 정확히 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Guild g WHERE g.id = :id")
    Optional<Guild> findByIdForUpdate(@Param("id") Long id);
}
