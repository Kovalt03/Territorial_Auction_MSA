package com.territorial.auction.domain.building;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZonePolicyTest {

    private long countZone(int gridSize, int zone1Radius, int zone2Radius, int zone) {
        long count = 0;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                if (ZonePolicy.calculateZone(x, y, gridSize, zone1Radius, zone2Radius) == zone) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    @DisplayName("짝수 격자 → Zone이 좌우 대칭")
    void calculateZone_evenGridIsSymmetric() {
        int gridSize = 10;
        for (int i = 0; i < gridSize; i++) {
            int left = ZonePolicy.calculateZone(i, 0, gridSize, 2, 4);
            int right = ZonePolicy.calculateZone(gridSize - 1 - i, 0, gridSize, 2, 4);
            assertThat(left).isEqualTo(right);
        }
    }

    @Test
    @DisplayName("홀수 격자 → Zone이 좌우 대칭")
    void calculateZone_oddGridIsSymmetric() {
        int gridSize = 15;
        for (int i = 0; i < gridSize; i++) {
            int left = ZonePolicy.calculateZone(i, 0, gridSize, 3, 6);
            int right = ZonePolicy.calculateZone(gridSize - 1 - i, 0, gridSize, 3, 6);
            assertThat(left).isEqualTo(right);
        }
    }

    @Test
    @DisplayName("섬 등급 D(10, 2/4) → Zone1 16칸, Zone2 48칸, Zone3 36칸")
    void calculateZone_islandGradeD() {
        assertThat(countZone(10, 2, 4, 1)).isEqualTo(16);
        assertThat(countZone(10, 2, 4, 2)).isEqualTo(48);
        assertThat(countZone(10, 2, 4, 3)).isEqualTo(36);
    }

    @Test
    @DisplayName("모든 등급에서 Zone3가 존재한다")
    void calculateZone_zone3AlwaysExists() {
        int[][] grades = {
            {10, 2, 4},
            {12, 2, 5},
            {15, 3, 6},
            {18, 4, 7},
            {20, 4, 8}, // 섬
            {8, 2, 3},
            {12, 2, 5},
            {16, 3, 6},
            {20, 4, 8} // 영토
        };
        for (int[] g : grades) {
            assertThat(countZone(g[0], g[1], g[2], 3))
                    .as("gridSize=%d zone1=%d zone2=%d", g[0], g[1], g[2])
                    .isPositive();
        }
    }

    @Test
    @DisplayName("중심 칸은 Zone1, 모서리 칸은 Zone3")
    void calculateZone_centerAndCorner() {
        assertThat(ZonePolicy.calculateZone(4, 4, 10, 2, 4)).isEqualTo(1);
        assertThat(ZonePolicy.calculateZone(0, 0, 10, 2, 4)).isEqualTo(3);
        assertThat(ZonePolicy.calculateZone(9, 9, 10, 2, 4)).isEqualTo(3);
    }
}
