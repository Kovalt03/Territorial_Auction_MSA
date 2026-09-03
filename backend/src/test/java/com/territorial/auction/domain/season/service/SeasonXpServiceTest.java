package com.territorial.auction.domain.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.combat.event.SiegeVictoryEvent;
import com.territorial.auction.domain.ranking.event.AuctionSettledEvent;
import com.territorial.auction.domain.season.SeasonPassPolicy;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.entity.SeasonPassProgress;
import com.territorial.auction.domain.season.repository.SeasonPassProgressRepository;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonXpServiceTest {

    @InjectMocks private SeasonXpService seasonXpService;

    @Mock private SeasonRepository seasonRepository;
    @Mock private SeasonPassProgressRepository seasonPassProgressRepository;
    @Mock private UserRepository userRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    private User user;
    private Season season;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .username("testuser")
                        .email("test@test.com")
                        .passwordHash("hash")
                        .nickname("tester")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        season =
                Season.builder()
                        .seasonNumber(1)
                        .startedAt(LocalDateTime.now().minusDays(10))
                        .endedAt(LocalDateTime.now().plusDays(20))
                        .build();
        ReflectionTestUtils.setField(season, "id", 1L);
    }

    // ─── handleAuctionWin() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("handleAuctionWin()")
    class HandleAuctionWin {

        @Test
        @DisplayName("기존 progress 없음 → 신규 생성 후 XP +100, level=1")
        void success_new_progress() {
            // given
            AuctionSettledEvent event = new AuctionSettledEvent(1L, 1L, 5000);
            SeasonPassProgress newProgress =
                    SeasonPassProgress.builder().user(user).season(season).build();

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonRepository.findById(1L)).willReturn(Optional.of(season));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.empty());
            given(seasonPassProgressRepository.save(any(SeasonPassProgress.class)))
                    .willReturn(newProgress);

            // when
            seasonXpService.handleAuctionWin(event);

            // then
            assertThat(newProgress.getXp()).isEqualTo(SeasonPassPolicy.XP_AUCTION_WIN);
            assertThat(newProgress.getLevel()).isEqualTo(1);
            then(seasonPassProgressRepository).should().save(any(SeasonPassProgress.class));
        }

        @Test
        @DisplayName("기존 progress 있음 → XP 누적")
        void success_existing_progress() {
            // given
            AuctionSettledEvent event = new AuctionSettledEvent(1L, 1L, 5000);
            SeasonPassProgress existing =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(existing, "xp", 200);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonRepository.findById(1L)).willReturn(Optional.of(season));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(existing));

            // when
            seasonXpService.handleAuctionWin(event);

            // then
            assertThat(existing.getXp()).isEqualTo(200 + SeasonPassPolicy.XP_AUCTION_WIN);
            then(seasonPassProgressRepository).should(never()).save(any(SeasonPassProgress.class));
        }

        @Test
        @DisplayName("xp=950이면 +100 → level 2, xp=50")
        void success_levelup() {
            // given
            AuctionSettledEvent event = new AuctionSettledEvent(1L, 1L, 5000);
            SeasonPassProgress existing =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(existing, "xp", 950);
            ReflectionTestUtils.setField(existing, "level", 1);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonRepository.findById(1L)).willReturn(Optional.of(season));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(existing));

            // when
            seasonXpService.handleAuctionWin(event);

            // then
            assertThat(existing.getLevel()).isEqualTo(2);
            assertThat(existing.getXp()).isEqualTo(50);
        }

        @Test
        @DisplayName("유저 없음 → 캐시 무효화 없이 정상 종료")
        void user_not_found() {
            // given
            AuctionSettledEvent event = new AuctionSettledEvent(1L, 1L, 5000);
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            // when
            seasonXpService.handleAuctionWin(event);

            // then
            then(seasonPassProgressRepository).should(never()).save(any());
            then(redisTemplate).should(never()).delete(any(String.class));
        }

        @Test
        @DisplayName("시즌 없음 → 정상 종료")
        void season_not_found() {
            // given
            AuctionSettledEvent event = new AuctionSettledEvent(1L, 99L, 5000);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonRepository.findById(99L)).willReturn(Optional.empty());

            // when
            seasonXpService.handleAuctionWin(event);

            // then
            then(seasonPassProgressRepository).should(never()).save(any());
            then(redisTemplate).should(never()).delete(any(String.class));
        }
    }

    // ─── handleSiegeVictory() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("handleSiegeVictory()")
    class HandleSiegeVictory {

        @Test
        @DisplayName("공성전 승리 → XP +50 적립")
        void success() {
            // given
            SiegeVictoryEvent event = new SiegeVictoryEvent(1L, 1L);
            SeasonPassProgress existing =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(existing, "xp", 0);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonRepository.findById(1L)).willReturn(Optional.of(season));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(existing));

            // when
            seasonXpService.handleSiegeVictory(event);

            // then
            assertThat(existing.getXp()).isEqualTo(SeasonPassPolicy.XP_SIEGE_VICTORY);
        }

        @Test
        @DisplayName("이벤트의 seasonId가 존재하지 않음 → 아무것도 하지 않음")
        void season_not_found() {
            // given
            SiegeVictoryEvent event = new SiegeVictoryEvent(1L, 99L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonRepository.findById(99L)).willReturn(Optional.empty());

            // when
            seasonXpService.handleSiegeVictory(event);

            // then
            then(seasonPassProgressRepository).should(never()).save(any());
        }
    }

    // ─── SeasonPassProgress.addXp() ───────────────────────────────────────────

    @Nested
    @DisplayName("addXp() — SeasonPassProgress 도메인 메서드")
    class AddXp {

        private SeasonPassProgress buildProgress(int initialXp, int initialLevel) {
            SeasonPassProgress progress =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(progress, "xp", initialXp);
            ReflectionTestUtils.setField(progress, "level", initialLevel);
            return progress;
        }

        @Test
        @DisplayName("xp=0 + 100 → xp=100, level=1 유지")
        void no_levelup() {
            // given
            SeasonPassProgress progress = buildProgress(0, 1);

            // when
            progress.addXp(100, SeasonPassPolicy.XP_PER_LEVEL);

            // then
            assertThat(progress.getXp()).isEqualTo(100);
            assertThat(progress.getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("xp=950 + 100 → level=2, xp=50")
        void levelup_once() {
            // given
            SeasonPassProgress progress = buildProgress(950, 1);

            // when
            progress.addXp(100, SeasonPassPolicy.XP_PER_LEVEL);

            // then
            assertThat(progress.getLevel()).isEqualTo(2);
            assertThat(progress.getXp()).isEqualTo(50);
        }

        @Test
        @DisplayName("xp=0 + 2100 → level=3, xp=100")
        void levelup_twice() {
            // given
            SeasonPassProgress progress = buildProgress(0, 1);

            // when
            progress.addXp(2100, SeasonPassPolicy.XP_PER_LEVEL);

            // then
            assertThat(progress.getLevel()).isEqualTo(3);
            assertThat(progress.getXp()).isEqualTo(100);
        }

        @Test
        @DisplayName("amount=0 → xp·level 변화 없음")
        void xp_zero_amount() {
            // given
            SeasonPassProgress progress = buildProgress(300, 2);

            // when
            progress.addXp(0, SeasonPassPolicy.XP_PER_LEVEL);

            // then
            assertThat(progress.getXp()).isEqualTo(300);
            assertThat(progress.getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("level=MAX_LEVEL 이미 최대 → XP·레벨 변화 없음")
        void already_max_level() {
            // given
            SeasonPassProgress progress = buildProgress(0, SeasonPassPolicy.MAX_LEVEL);

            // when
            progress.addXp(100, SeasonPassPolicy.XP_PER_LEVEL);

            // then
            assertThat(progress.getLevel()).isEqualTo(SeasonPassPolicy.MAX_LEVEL);
            assertThat(progress.getXp()).isEqualTo(0);
        }

        @Test
        @DisplayName("level=MAX_LEVEL-1, xp=950 + 100 → level=MAX_LEVEL, xp=0")
        void levelup_to_max() {
            // given
            SeasonPassProgress progress = buildProgress(950, SeasonPassPolicy.MAX_LEVEL - 1);

            // when
            progress.addXp(100, SeasonPassPolicy.XP_PER_LEVEL);

            // then
            assertThat(progress.getLevel()).isEqualTo(SeasonPassPolicy.MAX_LEVEL);
            assertThat(progress.getXp()).isEqualTo(0);
        }
    }
}
