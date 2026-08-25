package com.territorial.auction.domain.notification.repository;

import com.territorial.auction.domain.notification.entity.NotificationLog;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Slice<NotificationLog> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<NotificationLog> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndIsReadFalse(Long userId);

    @Modifying
    @Query(
            "UPDATE NotificationLog n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);
}
