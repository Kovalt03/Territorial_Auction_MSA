package com.territorial.auction.domain.season.repository;

import com.territorial.auction.domain.season.entity.UserMissionProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMissionProgressRepository extends JpaRepository<UserMissionProgress, Long> {

    List<UserMissionProgress> findByUser_IdAndMission_Season_Id(Long userId, Long seasonId);

    Optional<UserMissionProgress> findByUser_IdAndMission_Id(Long userId, Long missionId);
}
