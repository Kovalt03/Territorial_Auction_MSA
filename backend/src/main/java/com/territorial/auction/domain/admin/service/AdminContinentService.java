package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse.ContinentComposition;
import com.territorial.auction.domain.admin.dto.AdminGradeDistributionRequest;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryGradeRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContinentService {

    private static final List<String> GRADE_ORDER = List.of("S", "A", "B", "C", "D");

    private final ContinentRepository continentRepository;
    private final TerritoryRepository territoryRepository;
    private final TerritoryGradeRepository territoryGradeRepository;
    private final AdminAuditLogger adminAuditLogger;

    public AdminContinentCompositionResponse getCompositions() {
        Map<Long, Composition> byContinent = aggregate();
        List<ContinentComposition> continents =
                continentRepository.findAll().stream()
                        .sorted(
                                Comparator.comparing(
                                                Continent::getMinTrophyRequired,
                                                Comparator.nullsFirst(Comparator.naturalOrder()))
                                        .thenComparing(Continent::getId))
                        .map(c -> toComposition(c, byContinent.get(c.getId())))
                        .toList();
        return new AdminContinentCompositionResponse(continents);
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public ContinentComposition applyGradeDistribution(
            Long adminUserId, Long continentId, AdminGradeDistributionRequest request) {
        Continent continent =
                continentRepository
                        .findById(continentId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CONTINENT_NOT_FOUND));
        List<Territory> territories =
                territoryRepository.findAllByContinentIdWithDetails(continentId);
        validateDistribution(request.distribution(), territories.size());
        Map<String, TerritoryGrade> gradeByName = loadGrades(request.distribution().keySet());
        Map<String, Long> before = countByGrade(territories);

        reassign(territories, request.distribution(), gradeByName);

        adminAuditLogger.record(
                adminUserId,
                "CONTINENT_GRADE_DISTRIBUTION",
                "CONTINENT",
                continentId,
                Map.of(
                        "before",
                        before,
                        "after",
                        request.distribution(),
                        "reason",
                        request.reason() != null ? request.reason() : ""));
        return toComposition(continent, composeFrom(territories));
    }

    // 대륙(행성) 전체 영토의 경매 활성/비활성을 한 번에 변경한다.
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminBulkResultResponse changeContinentAuction(
            Long adminUserId, Long continentId, AdminToggleAuctionRequest request) {
        if (!continentRepository.existsById(continentId)) {
            throw new CustomException(ErrorCode.CONTINENT_NOT_FOUND);
        }
        int affected =
                territoryRepository.updateAuctionEnabledByContinentId(
                        continentId, request.enabled());
        adminAuditLogger.record(
                adminUserId,
                "CONTINENT_AUCTION_TOGGLE",
                "CONTINENT",
                continentId,
                Map.of(
                        "enabled",
                        request.enabled(),
                        "reason",
                        request.reason() != null ? request.reason() : ""));
        return new AdminBulkResultResponse(affected);
    }

    private void validateDistribution(Map<String, Integer> distribution, int total) {
        boolean anyNegative = distribution.values().stream().anyMatch(v -> v != null && v < 0);
        int sum = distribution.values().stream().mapToInt(v -> v == null ? 0 : v).sum();
        if (anyNegative || sum != total) {
            throw new CustomException(ErrorCode.GRADE_DISTRIBUTION_MISMATCH);
        }
    }

    private Map<String, TerritoryGrade> loadGrades(Set<String> gradeNames) {
        Map<String, TerritoryGrade> all =
                territoryGradeRepository.findAll().stream()
                        .collect(Collectors.toMap(TerritoryGrade::getGrade, g -> g));
        for (String name : gradeNames) {
            if (!all.containsKey(name)) {
                throw new CustomException(ErrorCode.TERRITORY_GRADE_NOT_FOUND);
            }
        }
        return all;
    }

    private Map<String, Long> countByGrade(List<Territory> territories) {
        Map<String, Long> map = new HashMap<>();
        for (Territory t : territories) {
            map.merge(t.getGrade().getGrade(), 1L, Long::sum);
        }
        return map;
    }

    private void reassign(
            List<Territory> territories,
            Map<String, Integer> distribution,
            Map<String, TerritoryGrade> gradeByName) {
        int idx = 0;
        for (String g : orderedGrades(distribution.keySet())) {
            int count = distribution.getOrDefault(g, 0);
            TerritoryGrade grade = gradeByName.get(g);
            for (int i = 0; i < count; i++) {
                territories.get(idx++).changeGrade(grade);
            }
        }
    }

    private List<String> orderedGrades(Set<String> keys) {
        List<String> ordered =
                new ArrayList<>(GRADE_ORDER.stream().filter(keys::contains).toList());
        keys.stream().filter(k -> !GRADE_ORDER.contains(k)).forEach(ordered::add);
        return ordered;
    }

    private Composition composeFrom(List<Territory> territories) {
        Composition comp = new Composition();
        for (Territory t : territories) {
            comp.add(t.getGrade().getGrade(), t.getStatus(), 1);
        }
        return comp;
    }

    private Map<Long, Composition> aggregate() {
        Map<Long, Composition> map = new HashMap<>();
        for (Object[] row : territoryRepository.aggregateCompositionGroupByContinent()) {
            Long continentId = (Long) row[0];
            String grade = (String) row[1];
            Territory.TerritoryStatus status = (Territory.TerritoryStatus) row[2];
            long count = (Long) row[3];
            map.computeIfAbsent(continentId, k -> new Composition()).add(grade, status, count);
        }
        return map;
    }

    private ContinentComposition toComposition(Continent continent, Composition comp) {
        Composition c = comp != null ? comp : new Composition();
        // 사용자 화면과 동일하게 displayName(행성명) 노출. 미설정 시 내부 name으로 폴백.
        String name =
                continent.getDisplayName() != null
                        ? continent.getDisplayName()
                        : continent.getName();
        return new ContinentComposition(
                continent.getId(),
                name,
                continent.getMinTrophyRequired(),
                c.total,
                c.gradeBreakdown,
                c.bidding,
                c.occupied,
                c.idle);
    }

    // 대륙별 등급·상태 집계 홀더
    private static final class Composition {
        private long total;
        private long bidding;
        private long occupied;
        private long idle;
        private final Map<String, Long> gradeBreakdown = new HashMap<>();

        private void add(String grade, Territory.TerritoryStatus status, long count) {
            total += count;
            gradeBreakdown.merge(grade, count, Long::sum);
            switch (status) {
                case BIDDING -> bidding += count;
                case OCCUPIED -> occupied += count;
                case IDLE -> idle += count;
            }
        }
    }
}
