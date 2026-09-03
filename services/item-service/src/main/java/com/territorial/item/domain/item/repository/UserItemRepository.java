package com.territorial.item.domain.item.repository;

import com.territorial.item.domain.item.entity.UserItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    Optional<UserItem> findByUserIdAndItem_Id(Long userId, Long itemId);

    @Query(
            value = "SELECT ui FROM UserItem ui JOIN FETCH ui.item WHERE ui.userId = :userId",
            countQuery = "SELECT COUNT(ui) FROM UserItem ui WHERE ui.userId = :userId")
    Page<UserItem> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.item WHERE ui.userId = :userId")
    List<UserItem> findAllByUserId(@Param("userId") Long userId);
}
