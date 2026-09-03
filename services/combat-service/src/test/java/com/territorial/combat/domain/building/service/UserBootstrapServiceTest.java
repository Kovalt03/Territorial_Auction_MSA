package com.territorial.combat.domain.building.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.entity.CombatUserSnapshot;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.entity.IslandGrade;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.building.repository.IslandGradeRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserBootstrapServiceTest {

    private static final long USER_ID = 1_000_000_000L;

    @InjectMocks private UserBootstrapService userBootstrapService;
    @Mock private CombatUserSnapshotRepository userSnapshotRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private IslandGradeRepository islandGradeRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    @Test
    void firstEventCreatesSnapshotVaultIslandAndCastle() {
        IslandGrade grade =
                IslandGrade.builder()
                        .name("D")
                        .gridSize(10)
                        .zone1Radius(2)
                        .zone2Radius(4)
                        .castleLevelRequired(1)
                        .build();
        BuildingType castle =
                BuildingType.builder()
                        .name("CASTLE")
                        .width(2)
                        .height(2)
                        .maxHp(100)
                        .baseCostGp(0)
                        .build();
        given(userSnapshotRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(globalVaultRepository.existsById(USER_ID)).willReturn(false);
        given(homeIslandRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(islandGradeRepository.findByName("D")).willReturn(Optional.of(grade));
        given(buildingTypeRepository.findByName("CASTLE")).willReturn(Optional.of(castle));
        given(homeIslandRepository.save(any(HomeIsland.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        userBootstrapService.bootstrap(USER_ID, "테스터");

        verify(userSnapshotRepository).save(any(CombatUserSnapshot.class));
        verify(globalVaultRepository).save(any(GlobalVault.class));
        verify(homeIslandRepository).save(any(HomeIsland.class));
        verify(buildingInstanceRepository).save(any());
    }

    @Test
    void replayUpdatesSnapshotButDoesNotDuplicateOwnedState() {
        CombatUserSnapshot snapshot =
                CombatUserSnapshot.builder()
                        .userId(USER_ID)
                        .nickname("이전닉")
                        .status("ACTIVE")
                        .build();
        HomeIsland island = HomeIsland.builder().userId(USER_ID).build();
        given(userSnapshotRepository.findById(USER_ID)).willReturn(Optional.of(snapshot));
        given(globalVaultRepository.existsById(USER_ID)).willReturn(true);
        given(homeIslandRepository.findByUserId(USER_ID)).willReturn(Optional.of(island));

        userBootstrapService.bootstrap(USER_ID, "새닉");

        verify(userSnapshotRepository, never()).save(any());
        verify(globalVaultRepository, never()).save(any());
        verify(islandGradeRepository, never()).findByName(any());
        verify(buildingInstanceRepository, never()).save(any());
    }
}
