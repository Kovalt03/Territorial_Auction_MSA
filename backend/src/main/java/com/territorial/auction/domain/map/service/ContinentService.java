package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.map.dto.ContinentListResponse;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContinentService {

    private final ContinentRepository continentRepository;
    private final TerritoryRepository territoryRepository;

    @Cacheable(value = "continent-list", key = "'all'")
    public ContinentListResponse getContinents() {
        List<Continent> continents = continentRepository.findAll();

        Map<Long, Long> totalCountMap =
                territoryRepository.countGroupByContinent().stream()
                        .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, Long> occupiedCountMap =
                territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED).stream()
                        .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        List<ContinentListResponse.ContinentInfo> continentInfos =
                continents.stream()
                        .map(
                                c ->
                                        ContinentListResponse.ContinentInfo.builder()
                                                .continentId(c.getId())
                                                .continentName(
                                                        c.getDisplayName() != null
                                                                ? c.getDisplayName()
                                                                : c.getName())
                                                .themeColor(c.getThemeColor())
                                                .grade(c.getGrade())
                                                .minTrophyRequired(c.getMinTrophyRequired())
                                                .description(c.getDescription())
                                                .totalTerritories(
                                                        totalCountMap
                                                                .getOrDefault(c.getId(), 0L)
                                                                .intValue())
                                                .occupiedTerritories(
                                                        occupiedCountMap
                                                                .getOrDefault(c.getId(), 0L)
                                                                .intValue())
                                                .dominantGuildName(null) // TODO: Guild 도메인 구현 후 연동
                                                .avgTerritorytGrade(null) // TODO: 등급별 집계 쿼리 구현 후 연동
                                                .bonusDescription(null) // TODO: BonusTile 연동 후 구현
                                                .build())
                        .toList();

        return new ContinentListResponse(continents.size(), continentInfos);
    }
}
