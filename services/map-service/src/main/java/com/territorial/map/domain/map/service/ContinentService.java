package com.territorial.map.domain.map.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.map.domain.map.dto.ContinentListResponse;
import com.territorial.map.domain.map.dto.ContinentTrophyBandResponse;
import com.territorial.map.domain.map.entity.Continent;
import com.territorial.map.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.map.domain.map.repository.ContinentRepository;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import com.territorial.map.global.exception.ErrorCode;
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
                                                .dominantGuildName(null) // TODO: Guild 연동 후
                                                .avgTerritorytGrade(null) // TODO: 등급 집계 후
                                                .bonusDescription(null) // TODO: BonusTile 연동 후
                                                .build())
                        .toList();

        return new ContinentListResponse(continents.size(), continentInfos);
    }

    // ranking-service 대륙 랭킹용 트로피 밴드. [이 대륙 minTrophyRequired, 다음 등급 대륙 minTrophyRequired).
    // 대륙은 공유 커널 map(map-service) 소유라 밴드 경계 계산을 여기서 담당한다.
    public ContinentTrophyBandResponse getTrophyBand(Long continentId) {
        Continent continent =
                continentRepository
                        .findById(continentId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CONTINENT_NOT_FOUND));
        int lower = continent.getMinTrophyRequired() != null ? continent.getMinTrophyRequired() : 0;
        Integer nextMin = continentRepository.findNextMinTrophyAbove(lower);
        int upper = nextMin != null ? nextMin : Integer.MAX_VALUE;
        return new ContinentTrophyBandResponse(lower, upper);
    }
}
