package com.territorial.auction.domain.social.service;

import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.social.entity.ChatRoom;
import com.territorial.auction.domain.social.entity.ChatRoom.ChatRoomType;
import com.territorial.auction.domain.social.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class ChatRoomSeeder implements ApplicationRunner {

    private final ChatRoomRepository chatRoomRepository;
    private final ContinentRepository continentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;

        if (chatRoomRepository.findByType(ChatRoomType.WORLD).isEmpty()) {
            chatRoomRepository.save(
                    ChatRoom.builder().type(ChatRoomType.WORLD).targetId(null).build());
            created++;
        }

        for (var continent : continentRepository.findAll()) {
            if (chatRoomRepository
                    .findByTypeAndTargetId(ChatRoomType.CONTINENT, continent.getId())
                    .isEmpty()) {
                chatRoomRepository.save(
                        ChatRoom.builder()
                                .type(ChatRoomType.CONTINENT)
                                .targetId(continent.getId())
                                .build());
                created++;
            }
        }

        if (created > 0) {
            log.info("chat_rooms 시드 완료 ({}건)", created);
        } else {
            log.info("chat_rooms 이미 존재 — 건너뜀");
        }
    }
}
