package com.territorial.combat.domain.building;

public final class ZonePolicy {

    private ZonePolicy() {}

    /**
     * 격자 중심으로부터의 체비쇼프 거리로 Zone(1=중심 … 3=외곽)을 판정한다.
     *
     * <p>0-indexed 격자의 실제 중심은 {@code (gridSize - 1) / 2} 로 짝수 크기에서는 반칸 위치에 놓인다. 정수 나눗셈으로 중심을 잡으면 한쪽
     * 변만 거리가 1 멀어져 Zone이 좌우 비대칭이 되므로, 좌표와 반경을 2배로 환산해 정수 연산으로 대칭을 유지한다.
     */
    public static int calculateZone(
            int posX, int posY, int gridSize, int zone1Radius, int zone2Radius) {
        int doubledCenter = gridSize - 1;
        int doubledDistance =
                Math.max(Math.abs(2 * posX - doubledCenter), Math.abs(2 * posY - doubledCenter));
        if (doubledDistance <= 2 * zone1Radius) return 1;
        if (doubledDistance <= 2 * zone2Radius) return 2;
        return 3;
    }
}
