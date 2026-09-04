package com.territorial.user.domain.user.repository;

import com.territorial.user.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    long countByStatus(String status);

    // 관리자 유저 검색: status 필터(nullable) + 닉네임/username 부분 일치.
    // keyword는 빈 문자열이면 전체 매치("%%"). null을 lower()에 넘기면 Postgres가
    // 타입을 bytea로 추론해 실패하므로, 서비스에서 항상 빈 문자열 이상을 전달한다.
    @Query(
            "SELECT u FROM User u WHERE (:status IS NULL OR u.status = :status) "
                    + "AND (LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) "
                    + "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchForAdmin(
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
