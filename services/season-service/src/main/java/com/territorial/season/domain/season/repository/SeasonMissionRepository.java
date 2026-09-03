package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.SeasonMission;
import com.territorial.season.domain.season.entity.SeasonMission.MissionTrigger;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonMissionRepository extends JpaRepository<SeasonMission, Long> {

    List<SeasonMission> findBySeason_IdOrderBySortOrderAsc(Long seasonId);

    List<SeasonMission> findBySeason_IdAndTriggerType(Long seasonId, MissionTrigger triggerType);

    boolean existsBySeason_Id(Long seasonId);
}
