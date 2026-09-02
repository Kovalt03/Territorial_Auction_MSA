package com.territorial.combat.domain.building.service;

import com.territorial.combat.domain.building.entity.BuildingInstance;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBootstrapService {

    private static final String DEFAULT_USER_STATUS = "ACTIVE";
    private static final String DEFAULT_ISLAND_GRADE = "D";
    private static final String CASTLE_TYPE = "CASTLE";

    private final CombatUserSnapshotRepository userSnapshotRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final IslandGradeRepository islandGradeRepository;
    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;

    @Transactional
    public void bootstrap(Long userId, String nickname) {
        upsertUserSnapshot(userId, nickname);
        createVaultIfMissing(userId);
        if (homeIslandRepository.findByUserId(userId).isPresent()) {
            return;
        }
        createIslandWithCastle(userId);
    }

    @Transactional
    public void updateProjectedNickname(Long userId, String nickname) {
        findSnapshot(userId).updateNickname(nickname);
    }

    @Transactional
    public void updateProjectedStatus(Long userId, String status) {
        findSnapshot(userId).updateStatus(status);
    }

    private void upsertUserSnapshot(Long userId, String nickname) {
        userSnapshotRepository
                .findById(userId)
                .ifPresentOrElse(
                        snapshot -> snapshot.updateNickname(nickname),
                        () ->
                                userSnapshotRepository.save(
                                        CombatUserSnapshot.builder()
                                                .userId(userId)
                                                .nickname(nickname)
                                                .status(DEFAULT_USER_STATUS)
                                                .build()));
    }

    private void createVaultIfMissing(Long userId) {
        if (!globalVaultRepository.existsById(userId)) {
            globalVaultRepository.save(GlobalVault.builder().userId(userId).build());
        }
    }

    private void createIslandWithCastle(Long userId) {
        IslandGrade grade = islandGradeRepository.findByName(DEFAULT_ISLAND_GRADE).orElseThrow();
        HomeIsland island =
                homeIslandRepository.save(
                        HomeIsland.builder().userId(userId).islandGrade(grade).build());
        createDefaultCastle(island);
    }

    private void createDefaultCastle(HomeIsland island) {
        BuildingType castle = buildingTypeRepository.findByName(CASTLE_TYPE).orElseThrow();
        int center = (island.getGridSize() / 2) - 1;
        buildingInstanceRepository.save(
                BuildingInstance.builder()
                        .island(island)
                        .buildingType(castle)
                        .posX(center)
                        .posY(center)
                        .hp(castle.getMaxHp())
                        .zone(1)
                        .build());
    }

    private CombatUserSnapshot findSnapshot(Long userId) {
        return userSnapshotRepository.findById(userId).orElseThrow();
    }
}
