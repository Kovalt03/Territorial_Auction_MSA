package com.territorial.auction.domain.building.service;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.entity.IslandGrade;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.building.repository.IslandGradeRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserBootstrapService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final IslandGradeRepository islandGradeRepository;
    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;

    public void bootstrap(Long userId, String username, String email, String nickname) {
        insertUserProjection(userId, username, email, nickname);
        if (homeIslandRepository.findByUserId(userId).isPresent()) {
            return;
        }
        User user = userRepository.getReferenceById(userId);
        IslandGrade grade = islandGradeRepository.findByName("D").orElseThrow();
        HomeIsland island =
                homeIslandRepository.save(
                        HomeIsland.builder().user(user).islandGrade(grade).build());
        createDefaultCastle(island);
    }

    private void insertUserProjection(Long userId, String username, String email, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, password_hash, nickname, status, role) "
                        + "VALUES (?, ?, ?, '!', ?, 'ACTIVE', 'USER') ON CONFLICT (id) DO NOTHING",
                userId,
                username,
                email,
                nickname);
    }

    private void createDefaultCastle(HomeIsland island) {
        BuildingType castle = buildingTypeRepository.findByName("CASTLE").orElseThrow();
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
}
