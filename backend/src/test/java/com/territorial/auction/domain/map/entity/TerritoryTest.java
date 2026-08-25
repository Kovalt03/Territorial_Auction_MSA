package com.territorial.auction.domain.map.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.territorial.auction.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.auction.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TerritoryTest {

    private Territory territory;
    private User owner;

    @BeforeEach
    void setUp() {
        territory = Territory.builder().coordX(3).coordY(7).build();
        ReflectionTestUtils.setField(territory, "id", 1L);

        owner =
                User.builder()
                        .username("owner1")
                        .email("owner1@test.com")
                        .passwordHash("hashed")
                        .nickname("점령자")
                        .build();
        ReflectionTestUtils.setField(owner, "id", 10L);

        // OCCUPIED 상태로 설정
        LocalDateTime until = LocalDateTime.of(2026, 6, 1, 0, 0);
        territory.occupy(owner, until, until.minusHours(12));
    }

    @Nested
    @DisplayName("release()")
    class Release {

        @Test
        @DisplayName("release() 호출 → status가 IDLE로 변경된다")
        void release_setsStatusIdle() {
            LocalDateTime nextAuctionAt = LocalDateTime.of(2026, 6, 2, 0, 0);

            territory.release(nextAuctionAt);

            assertThat(territory.getStatus()).isEqualTo(TerritoryStatus.IDLE);
        }

        @Test
        @DisplayName("release() 호출 → owner가 null로 초기화된다")
        void release_clearsOwner() {
            assertThat(territory.getOwner()).isNotNull();

            territory.release(LocalDateTime.of(2026, 6, 2, 0, 0));

            assertThat(territory.getOwner()).isNull();
        }

        @Test
        @DisplayName("release() 호출 → nextAuctionAt이 인자값으로 설정된다")
        void release_setsNextAuctionAt() {
            LocalDateTime nextAuctionAt = LocalDateTime.of(2026, 6, 3, 12, 0);

            territory.release(nextAuctionAt);

            assertThat(territory.getNextAuctionAt()).isEqualTo(nextAuctionAt);
        }

        @Test
        @DisplayName("release() 호출 → occupiedUntil이 null로 초기화된다")
        void release_clearsOccupiedUntil() {
            territory.release(LocalDateTime.of(2026, 6, 2, 0, 0));

            assertThat(territory.getOccupiedUntil()).isNull();
        }
    }
}
