package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.social.entity.ChatRoom;

public record AdminChatRoomResponse(Long roomId, String type, Long targetId, String label) {

    public static AdminChatRoomResponse from(ChatRoom room) {
        return new AdminChatRoomResponse(
                room.getId(), room.getType().name(), room.getTargetId(), room.toRoomId());
    }
}
