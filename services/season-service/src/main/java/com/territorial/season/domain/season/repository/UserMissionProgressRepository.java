package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.UserMissionProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMissionProgressRepository extends JpaRepository<UserMissionProgress, Long> {

    List<UserMissionProgress> findByUserIdAndMission_Season_Id(Long userId, Long seasonId);

    Optional<UserMissionProgress> findByUserIdAndMission_Id(Long userId, Long missionId);
}
