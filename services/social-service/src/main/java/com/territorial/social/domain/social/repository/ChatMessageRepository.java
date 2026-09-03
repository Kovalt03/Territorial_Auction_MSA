package com.territorial.social.domain.social.repository;

import com.territorial.social.domain.social.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoom_IdOrderByIdDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoom_IdAndIdLessThanOrderByIdDesc(
            Long roomId, Long beforeId, Pageable pageable);

    // 관리자 검열: roomId 필터(nullable) + 내용 부분검색. keyword는 빈 문자열이면 전체 매치.
    @Query(
            value =
                    "SELECT m FROM ChatMessage m JOIN FETCH m.room "
                            + "WHERE (:roomId IS NULL OR m.room.id = :roomId) "
                            + "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%'))",
            countQuery =
                    "SELECT COUNT(m) FROM ChatMessage m "
                            + "WHERE (:roomId IS NULL OR m.room.id = :roomId) "
                            + "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<ChatMessage> searchForAdmin(
            @Param("roomId") Long roomId, @Param("keyword") String keyword, Pageable pageable);
}
