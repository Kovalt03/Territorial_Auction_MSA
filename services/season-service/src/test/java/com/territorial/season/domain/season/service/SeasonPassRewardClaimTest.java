package com.territorial.season.domain.season.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.season.client.CombatResourceClient;
import com.territorial.season.client.ItemGrantClient;
import com.territorial.season.client.WalletClient;
import com.territorial.season.domain.season.entity.RewardItemType;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonPassLevelReward;
import com.territorial.season.domain.season.entity.SeasonPassLevelReward.RewardKind;
import com.territorial.season.domain.season.entity.SeasonPassLevelReward.RewardTrack;
import com.territorial.season.domain.season.repository.SeasonPassLevelRewardRepository;
import com.territorial.season.domain.season.repository.SeasonPassProgressRepository;
import com.territorial.season.domain.season.repository.SeasonPassRewardClaimRepository;
import com.territorial.season.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonPassRewardClaimTest {

    @InjectMocks private SeasonPassService seasonPassService;

    @Mock private SeasonPassLevelRewardRepository seasonPassLevelRewardRepository;
    @Mock private SeasonPassProgressRepository seasonPassProgressRepository;
    @Mock private SeasonPassRewardClaimRepository seasonPassRewardClaimRepository;
    @Mock private ItemGrantClient itemGrantClient;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private WalletClient walletClient;

    private static final Long USER_ID = 7L;
    private static final Long REWARD_ID = 11L;

    private SeasonPassLevelReward freeItemReward() {
        Season season = Season.builder().build();
        ReflectionTestUtils.setField(season, "id", 1L);
        SeasonPassLevelReward reward =
                SeasonPassLevelReward.builder()
                        .season(season)
                        .level(1)
                        .track(RewardTrack.FREE)
                        .rewardName("공격권")
                        .rewardKind(RewardKind.ITEM)
                        .itemType(RewardItemType.ATTACK_NORMAL)
                        .quantity(1)
                        .build();
        ReflectionTestUtils.setField(reward, "id", REWARD_ID);
        return reward;
    }

    @DisplayName("동시 수령 — 마커 flush가 UNIQUE 위반으로 실패하면 원격 지급 없이 REWARD_ALREADY_CLAIMED")
    @Test
    void claimReward_concurrentClaim_failsBeforeGrant() {
        when(seasonPassLevelRewardRepository.findById(REWARD_ID))
                .thenReturn(Optional.of(freeItemReward()));
        when(seasonPassProgressRepository.findByUserIdAndSeason_Id(USER_ID, 1L))
                .thenReturn(Optional.empty());
        when(seasonPassRewardClaimRepository.existsByUserIdAndReward_Id(USER_ID, REWARD_ID))
                .thenReturn(false);
        when(seasonPassRewardClaimRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_reward_claim_user_reward"));

        assertThatThrownBy(() -> seasonPassService.claimReward(USER_ID, REWARD_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REWARD_ALREADY_CLAIMED);

        verify(itemGrantClient, never()).grantByType(anyLong(), anyString(), anyInt());
        verify(combatResourceClient, never()).creditGp(anyLong(), anyInt(), anyString());
    }
}
