package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerritoryService {

    private final TerritoryRepository territoryRepository;
    private final UserRepository userRepository;

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void occupy(
            Long territoryId,
            Long winnerId,
            LocalDateTime occupiedUntil,
            LocalDateTime protectedUntil) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        // FK만 세팅 — getReferenceById는 프록시라 실제 조회가 없다(occupy가 winner 속성을 읽지 않음).
        // winnerId는 낙찰자라 항상 유효. map 분리 시 Territory.owner→ownerId로 바뀌면 이 로드도 사라짐.
        User winner = userRepository.getReferenceById(winnerId);
        territory.occupy(winner, occupiedUntil, protectedUntil);
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void release(Long territoryId, LocalDateTime nextAuctionAt) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        territory.release(nextAuctionAt);
    }
}
