package com.territorial.auction.domain.item.repository;

import com.territorial.auction.domain.item.entity.UserItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    Optional<UserItem> findByUser_IdAndItem_Id(Long userId, Long itemId);

    @Query(
            value = "SELECT ui FROM UserItem ui JOIN FETCH ui.item WHERE ui.user.id = :userId",
            countQuery = "SELECT COUNT(ui) FROM UserItem ui WHERE ui.user.id = :userId")
    Page<UserItem> findByUser_Id(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.item WHERE ui.user.id = :userId")
    List<UserItem> findAllByUser_Id(@Param("userId") Long userId);
}
