package com.territorial.map.internal.admin;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.map.client.NicknameClient;
import com.territorial.map.domain.map.entity.Continent;
import com.territorial.map.domain.map.entity.Territory;
import com.territorial.map.domain.map.entity.TerritoryGrade;
import com.territorial.map.domain.map.repository.ContinentRepository;
import com.territorial.map.domain.map.repository.TerritoryGradeRepository;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import com.territorial.map.domain.map.service.TerritoryAuctionReadyPublisher;
import com.territorial.map.global.exception.ErrorCode;
import com.territorial.map.internal.admin.dto.AdminAffectedResult;
import com.territorial.map.internal.admin.dto.AdminContinentCompositionResponse;
import com.territorial.map.internal.admin.dto.AdminContinentCompositionResponse.ContinentComposition;
import com.territorial.map.internal.admin.dto.AdminGradeDistributionResult;
import com.territorial.map.internal.admin.dto.AdminStatusCounts;
import com.territorial.map.internal.admin.dto.AdminTerritoryChangeResult;
import com.territorial.map.internal.admin.dto.AdminTerritoryView;
import com.territorial.map.internal.admin.dto.AdminUserTerritoryPage;
import com.territorial.map.internal.admin.dto.AdminUserTerritoryView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 영토·대륙 관리(내부 계약). 모놀리식 admin이 감사 로그를 남기며 이 서비스에 위임한다. 변경 메서드는 감사용 변경 전 값을 함께 반환한다. 영토 그리드/ETag
 * 캐시는 변경 시 무효화한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapAdminService {

    private static final List<String> GRADE_ORDER = List.of("S", "A", "B", "C", "D");

    private final ContinentRepository continentRepository;
    private final TerritoryRepository territoryRepository;
    private final TerritoryGradeRepository territoryGradeRepository;
    private final TerritoryAuctionReadyPublisher territoryAuctionReadyPublisher;
    private final NicknameClient nicknameClient;

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

    public List<AdminTerritoryView> getContinentTerritories(Long continentId) {
        if (!continentRepository.existsById(continentId)) {
            throw new CustomException(ErrorCode.CONTINENT_NOT_FOUND);
        }
        return toViews(territoryRepository.findAllByContinentIdWithDetails(continentId));
    }

    public AdminUserTerritoryPage getUserTerritories(Long userId, int page, int size) {
        Page<Territory> territoryPage =
                territoryRepository.findAllByUserId(userId, PageRequest.of(page, size));
        List<AdminUserTerritoryView> content =
                territoryPage.getContent().stream().map(AdminUserTerritoryView::from).toList();
        return new AdminUserTerritoryPage(content, territoryPage.getTotalElements());
    }

    public AdminStatusCounts getStatusCounts() {
        return new AdminStatusCounts(
                territoryRepository.countByStatus(Territory.TerritoryStatus.BIDDING),
                territoryRepository.countByStatus(Territory.TerritoryStatus.OCCUPIED),
                territoryRepository.countByStatus(Territory.TerritoryStatus.IDLE));
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminGradeDistributionResult applyGradeDistribution(
            Long continentId, Map<String, Integer> distribution) {
        Continent continent =
                continentRepository
                        .findById(continentId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CONTINENT_NOT_FOUND));
        List<Territory> territories =
                new ArrayList<>(territoryRepository.findAllByContinentIdWithDetails(continentId));
        validateDistribution(distribution, territories.size());
        Map<String, TerritoryGrade> gradeByName = loadGrades(distribution.keySet());
        Map<String, Long> before = countByGrade(territories);

        reassign(territories, distribution, gradeByName);

        return new AdminGradeDistributionResult(
                before, toComposition(continent, composeFrom(territories)));
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminAffectedResult changeContinentAuction(Long continentId, boolean enabled) {
        if (!continentRepository.existsById(continentId)) {
            throw new CustomException(ErrorCode.CONTINENT_NOT_FOUND);
        }
        return new AdminAffectedResult(
                territoryRepository.updateAuctionEnabledByContinentId(continentId, enabled));
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminTerritoryChangeResult changeGrade(Long territoryId, String gradeName) {
        Territory territory = findTerritoryOrThrow(territoryId);
        validateNotOccupied(territory);
        TerritoryGrade grade = findGradeOrThrow(gradeName);
        String before = territory.getGrade().getGrade();
        territory.changeGrade(grade);
        return new AdminTerritoryChangeResult(before, null, toView(territory));
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminTerritoryChangeResult changeAuctionEnabled(Long territoryId, boolean enabled) {
        Territory territory = findTerritoryOrThrow(territoryId);
        boolean before = territory.getAuctionEnabled();
        territory.changeAuctionEnabled(enabled);
        return new AdminTerritoryChangeResult(null, before, toView(territory));
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminTerritoryChangeResult forceStartAuction(Long territoryId) {
        Territory territory = findTerritoryOrThrow(territoryId);
        validateIdle(territory);
        territory.startBidding();
        territoryAuctionReadyPublisher.publishFor(territory);
        return new AdminTerritoryChangeResult(null, null, toView(territory));
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public List<AdminTerritoryChangeResult> bulkChangeGrade(
            String gradeName, List<Long> territoryIds) {
        TerritoryGrade grade = findGradeOrThrow(gradeName);
        List<AdminTerritoryChangeResult> changed = new ArrayList<>();
        for (Long territoryId : territoryIds.stream().distinct().toList()) {
            Territory territory = findTerritoryOrThrow(territoryId);
            if (territory.getStatus() == Territory.TerritoryStatus.OCCUPIED) {
                continue;
            }
            String before = territory.getGrade().getGrade();
            territory.changeGrade(grade);
            changed.add(new AdminTerritoryChangeResult(before, null, toView(territory)));
        }
        return changed;
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public List<AdminTerritoryChangeResult> bulkChangeAuction(
            boolean enabled, List<Long> territoryIds) {
        List<AdminTerritoryChangeResult> changed = new ArrayList<>();
        for (Long territoryId : territoryIds.stream().distinct().toList()) {
            Territory territory = findTerritoryOrThrow(territoryId);
            boolean before = territory.getAuctionEnabled();
            territory.changeAuctionEnabled(enabled);
            changed.add(new AdminTerritoryChangeResult(null, before, toView(territory)));
        }
        return changed;
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public List<AdminTerritoryChangeResult> bulkForceStart(List<Long> territoryIds) {
        List<AdminTerritoryChangeResult> started = new ArrayList<>();
        for (Long territoryId : territoryIds.stream().distinct().toList()) {
            Territory territory = findTerritoryOrThrow(territoryId);
            if (territory.getStatus() != Territory.TerritoryStatus.IDLE) {
                continue;
            }
            territory.startBidding();
            territoryAuctionReadyPublisher.publishFor(territory);
            started.add(new AdminTerritoryChangeResult(null, null, toView(territory)));
        }
        return started;
    }

    private List<AdminTerritoryView> toViews(List<Territory> territories) {
        List<Long> ownerIds =
                territories.stream()
                        .map(Territory::getOwnerId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, String> nicknames = nicknameClient.getNicknames(ownerIds);
        return territories.stream()
                .map(
                        t ->
                                AdminTerritoryView.from(
                                        t,
                                        t.getOwnerId() != null
                                                ? nicknames.get(t.getOwnerId())
                                                : null))
                .toList();
    }

    private AdminTerritoryView toView(Territory territory) {
        String nickname =
                territory.getOwnerId() != null
                        ? nicknameClient.getNickname(territory.getOwnerId())
                        : null;
        return AdminTerritoryView.from(territory, nickname);
    }

    private Territory findTerritoryOrThrow(Long territoryId) {
        return territoryRepository
                .findById(territoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
    }

    private TerritoryGrade findGradeOrThrow(String gradeName) {
        return territoryGradeRepository
                .findByGrade(gradeName)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_GRADE_NOT_FOUND));
    }

    private void validateIdle(Territory territory) {
        if (territory.getStatus() != Territory.TerritoryStatus.IDLE) {
            throw new CustomException(ErrorCode.TERRITORY_NOT_IDLE);
        }
    }

    private void validateNotOccupied(Territory territory) {
        if (territory.getStatus() == Territory.TerritoryStatus.OCCUPIED) {
            throw new CustomException(ErrorCode.TERRITORY_GRADE_LOCKED_OCCUPIED);
        }
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
