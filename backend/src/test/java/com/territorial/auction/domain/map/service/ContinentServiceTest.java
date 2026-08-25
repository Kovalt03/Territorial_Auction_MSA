package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.map.dto.ContinentListResponse;
import com.territorial.auction.domain.map.dto.ContinentListResponse.ContinentInfo;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContinentServiceTest {

    @InjectMocks private ContinentService continentService;

    @Mock private ContinentRepository continentRepository;
    @Mock private TerritoryRepository territoryRepository;

    // ────────────────────────────────────────────────────────────────
    // Fixtures
    // ────────────────────────────────────────────────────────────────

    private Continent continent(Long id, String name) {
        Continent c = Continent.builder().name(name).themeColor("#FF4444").build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    /** Object[] row matching the batch query result format: [continentId, count] */
    private Object[] row(Long continentId, Long count) {
        return new Object[] {continentId, count};
    }

    // ────────────────────────────────────────────────────────────────
    // getContinents()
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getContinents()")
    class GetContinents {

        @Test
        @DisplayName("배치 쿼리 사용 — countGroupByContinent, countByStatusGroupByContinent 각 1회 호출")
        void getContinents_usesBatchQueries() {
            Continent c1 = continent(1L, "붉은 사막");
            Continent c2 = continent(2L, "얼음 벌판");
            given(continentRepository.findAll()).willReturn(List.of(c1, c2));
            given(territoryRepository.countGroupByContinent())
                    .willReturn(List.<Object[]>of(row(1L, 10L), row(2L, 8L)));
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.<Object[]>of(row(1L, 3L), row(2L, 5L)));

            continentService.getContinents();

            // 배치 쿼리 각 1회 — N+1 쿼리 메서드 미호출
            then(territoryRepository).should().countGroupByContinent();
            then(territoryRepository)
                    .should()
                    .countByStatusGroupByContinent(TerritoryStatus.OCCUPIED);
            then(territoryRepository).should(never()).countByContinentId(1L);
            then(territoryRepository).should(never()).countByContinentId(2L);
            then(territoryRepository)
                    .should(never())
                    .countByContinentIdAndStatus(1L, TerritoryStatus.OCCUPIED);
            then(territoryRepository)
                    .should(never())
                    .countByContinentIdAndStatus(2L, TerritoryStatus.OCCUPIED);
        }

        @Test
        @DisplayName("대륙이 존재하면 전체 목록과 totalContinents를 반환한다")
        void getContinents_returnsList() {
            Continent c1 = continent(1L, "붉은 사막");
            Continent c2 = continent(2L, "얼음 벌판");
            given(continentRepository.findAll()).willReturn(List.of(c1, c2));
            given(territoryRepository.countGroupByContinent())
                    .willReturn(List.<Object[]>of(row(1L, 10L), row(2L, 8L)));
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.<Object[]>of(row(1L, 3L), row(2L, 5L)));

            ContinentListResponse response = continentService.getContinents();

            assertThat(response.totalContinents()).isEqualTo(2);
            assertThat(response.continent()).hasSize(2);
        }

        @Test
        @DisplayName("대륙 ID와 이름이 올바르게 매핑된다")
        void getContinents_mapsIdAndName() {
            Continent c = continent(1L, "붉은 사막");
            given(continentRepository.findAll()).willReturn(List.of(c));
            given(territoryRepository.countGroupByContinent())
                    .willReturn(List.<Object[]>of(row(1L, 0L)));
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.of());

            ContinentInfo info = continentService.getContinents().continent().get(0);

            assertThat(info.continentId()).isEqualTo(1L);
            assertThat(info.continentName()).isEqualTo("붉은 사막");
        }

        @Test
        @DisplayName("totalTerritories는 배치 결과의 합산 값을 대륙별로 매핑한다")
        void getContinents_totalTerritories() {
            Continent c = continent(1L, "붉은 사막");
            given(continentRepository.findAll()).willReturn(List.of(c));
            given(territoryRepository.countGroupByContinent())
                    .willReturn(List.<Object[]>of(row(1L, 15L)));
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.of());

            ContinentInfo info = continentService.getContinents().continent().get(0);

            assertThat(info.totalTerritories()).isEqualTo(15);
        }

        @Test
        @DisplayName("occupiedTerritories는 OCCUPIED 배치 결과를 대륙별로 매핑한다")
        void getContinents_occupiedTerritories() {
            Continent c = continent(1L, "붉은 사막");
            given(continentRepository.findAll()).willReturn(List.of(c));
            given(territoryRepository.countGroupByContinent())
                    .willReturn(List.<Object[]>of(row(1L, 10L)));
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.<Object[]>of(row(1L, 4L)));

            ContinentInfo info = continentService.getContinents().continent().get(0);

            assertThat(info.occupiedTerritories()).isEqualTo(4);
        }

        @Test
        @DisplayName("배치 결과에 해당 대륙 ID가 없으면 count 0으로 처리한다")
        void getContinents_missingInBatchResult_defaultsToZero() {
            Continent c = continent(99L, "신대륙");
            given(continentRepository.findAll()).willReturn(List.of(c));
            // 배치 결과에 continentId=99 없음
            given(territoryRepository.countGroupByContinent()).willReturn(List.of());
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.of());

            ContinentInfo info = continentService.getContinents().continent().get(0);

            assertThat(info.totalTerritories()).isZero();
            assertThat(info.occupiedTerritories()).isZero();
        }

        @Test
        @DisplayName("미구현 필드(dominantGuildName, avgTerritorytGrade, bonusDescription)는 null이다")
        void getContinents_todoFieldsAreNull() {
            Continent c = continent(1L, "붉은 사막");
            given(continentRepository.findAll()).willReturn(List.of(c));
            given(territoryRepository.countGroupByContinent()).willReturn(List.of());
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.of());

            ContinentInfo info = continentService.getContinents().continent().get(0);

            assertThat(info.dominantGuildName()).isNull();
            assertThat(info.avgTerritorytGrade()).isNull();
            assertThat(info.bonusDescription()).isNull();
        }

        @Test
        @DisplayName("대륙이 없으면 빈 목록과 totalContinents 0을 반환한다")
        void getContinents_empty() {
            given(continentRepository.findAll()).willReturn(List.of());
            given(territoryRepository.countGroupByContinent()).willReturn(List.of());
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.of());

            ContinentListResponse response = continentService.getContinents();

            assertThat(response.totalContinents()).isZero();
            assertThat(response.continent()).isEmpty();
        }

        @Test
        @DisplayName("여러 대륙의 영토 수가 배치 결과 기반으로 각각 독립 집계된다")
        void getContinents_multipleContinent_eachCountIndependent() {
            Continent c1 = continent(1L, "붉은 사막");
            Continent c2 = continent(2L, "얼음 벌판");
            given(continentRepository.findAll()).willReturn(List.of(c1, c2));
            given(territoryRepository.countGroupByContinent())
                    .willReturn(List.<Object[]>of(row(1L, 20L), row(2L, 5L)));
            given(territoryRepository.countByStatusGroupByContinent(TerritoryStatus.OCCUPIED))
                    .willReturn(List.<Object[]>of(row(1L, 10L), row(2L, 2L)));

            List<ContinentInfo> infos = continentService.getContinents().continent();

            assertThat(infos.get(0).totalTerritories()).isEqualTo(20);
            assertThat(infos.get(0).occupiedTerritories()).isEqualTo(10);
            assertThat(infos.get(1).totalTerritories()).isEqualTo(5);
            assertThat(infos.get(1).occupiedTerritories()).isEqualTo(2);
        }
    }
}
