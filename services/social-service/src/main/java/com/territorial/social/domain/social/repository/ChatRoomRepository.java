package com.territorial.social.domain.social.repository;

import com.territorial.social.domain.social.entity.ChatRoom;
import com.territorial.social.domain.social.entity.ChatRoom.ChatRoomType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByType(ChatRoomType type);

    Optional<ChatRoom> findByTypeAndTargetId(ChatRoomType type, Long targetId);
}
