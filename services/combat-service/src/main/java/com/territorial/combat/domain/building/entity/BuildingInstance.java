package com.territorial.combat.domain.building.entity;

import com.territorial.combat.domain.building.BuildingPolicy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "building_instances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "territory_id")
    private Long territoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "island_id")
    private HomeIsland island;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_type_id", nullable = false)
    private BuildingType buildingType;

    // 보관함 소유자 — territory/island 둘 다 null일 때(보관 상태) 사용
    @Column(name = "user_id")
    private Long ownerId;

    @Column(name = "pos_x", nullable = false)
    private Integer posX;

    @Column(name = "pos_y", nullable = false)
    private Integer posY;

    @Column(nullable = false)
    private Integer hp;

    @Column(nullable = false)
    private Integer level = 1;

    @Column(nullable = false)
    private Integer zone;

    @Column(nullable = false)
    private boolean isDestroyed = false;

    // 성·저장소가 사용 — 그 위치에 적립된 GP
    @Column(nullable = false)
    private Integer storedGp = 0;

    // 성·저장소가 사용 — 그 위치에 적립된 식량. 약탈·이전 불가
    @Column(nullable = false)
    private Integer storedFood = 0;

    // WORKSHOP 파괴 후 일정 시간 생산 중단 — null이면 디버프 없음
    @Column private LocalDateTime workshopDebuffUntil;

    // 건설/업그레이드 완료 예정 시각 — null이면 완성된 건물. 장인 슬롯은 이 값이 미래인 건물만 점유한다.
    @Column private LocalDateTime buildCompleteAt;

    // 업그레이드 대기 중일 때 도달할 레벨. null이면 신축 대기이거나 대기 없음.
    @Column private Integer upgradeToLevel;

    // 수리 진행 중 여부. buildCompleteAt(=수리 완료 시각) 동안 true — 수리 중에는 생산·방어 비활성.
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isRepairing = false;

    @Builder
    public BuildingInstance(
            Long territoryId,
            HomeIsland island,
            BuildingType buildingType,
            Long ownerId,
            Integer posX,
            Integer posY,
            Integer hp,
            Integer zone) {
        this.territoryId = territoryId;
        this.island = island;
        this.buildingType = buildingType;
        this.ownerId = ownerId;
        this.posX = posX;
        this.posY = posY;
        this.hp = hp;
        this.zone = zone;
    }

    public void upgrade() {
        this.level++;
        this.hp = BuildingPolicy.scaledMaxHp(this.buildingType.getMaxHp(), this.level);
    }

    // 레벨별 지정 HP가 있으면 현재 HP를 그 값으로 맞춘다(업그레이드 직후 풀피).
    public void applyLevelMaxHp(int maxHp) {
        this.hp = maxHp;
    }

    public void repair() {
        this.hp = BuildingPolicy.scaledMaxHp(this.buildingType.getMaxHp(), this.level);
        this.isDestroyed = false;
    }

    public void store(Long userId) {
        this.ownerId = userId;
        this.territoryId = null;
        this.island = null;
        this.posX = -1;
        this.posY = -1;
        this.zone = 0;
    }

    public void movePosition(int posX, int posY, int zone) {
        this.posX = posX;
        this.posY = posY;
        this.zone = zone;
    }

    public void placeOnTerritory(Long territoryId, int posX, int posY, int zone) {
        this.territoryId = territoryId;
        this.island = null;
        this.ownerId = null;
        this.posX = posX;
        this.posY = posY;
        this.zone = zone;
    }

    public void placeOnIsland(HomeIsland island, int posX, int posY, int zone) {
        this.island = island;
        this.territoryId = null;
        this.ownerId = null;
        this.posX = posX;
        this.posY = posY;
        this.zone = zone;
    }

    public boolean isInInventory() {
        return this.territoryId == null && this.island == null;
    }

    public Long ownerId() {
        if (island != null) {
            return island.getUserId();
        }
        return ownerId;
    }

    public void startConstruction(LocalDateTime completeAt) {
        this.buildCompleteAt = completeAt;
    }

    // 업그레이드는 완료 시점에 레벨이 오른다 — 대기 중에는 기존 레벨·HP를 유지한다.
    public void startUpgrade(int targetLevel, LocalDateTime completeAt) {
        this.upgradeToLevel = targetLevel;
        this.buildCompleteAt = completeAt;
    }

    // 수리 시작 — 완료 시각까지 buildCompleteAt으로 잠긴다(그 동안 비활성). 완료 시 HP 풀피(호출자 반영).
    public void startRepair(LocalDateTime completeAt) {
        this.isRepairing = true;
        this.buildCompleteAt = completeAt;
    }

    public boolean isUnderConstruction(LocalDateTime now) {
        return buildCompleteAt != null && buildCompleteAt.isAfter(now);
    }

    // 완료 시각이 지났는데 아직 정리되지 않은 상태
    public boolean isConstructionFinished(LocalDateTime now) {
        return buildCompleteAt != null && !buildCompleteAt.isAfter(now);
    }

    // 대기 종료 — 업그레이드였다면 레벨을 올린다. HP는 호출자가 레벨 스펙을 반영해 맞춘다(수리·업글 시 풀피).
    public void finishConstruction() {
        if (upgradeToLevel != null) {
            this.level = upgradeToLevel;
            this.upgradeToLevel = null;
        }
        if (isRepairing) {
            this.isDestroyed = false; // 수리 완료 → 파괴 상태 해제(풀피는 호출자가 반영)
        }
        this.isRepairing = false;
        this.buildCompleteAt = null;
    }

    public void applyWorkshopDebuff(LocalDateTime until) {
        this.workshopDebuffUntil = until;
    }

    public boolean isWorkshopDebuffActive(LocalDateTime now) {
        return workshopDebuffUntil != null && workshopDebuffUntil.isAfter(now);
    }

    public void takeDamage(int damage) {
        this.hp = Math.max(0, this.hp - damage);
        if (this.hp == 0) {
            this.isDestroyed = true;
        }
    }

    public int loot(int amount) {
        int actual = Math.min(amount, this.storedGp);
        this.storedGp -= actual;
        return actual;
    }

    public void addStoredGp(int amount) {
        this.storedGp += amount;
    }

    // 이 건물이 담을 수 있는 남은 GP 공간까지만 넣고, 실제로 넣은 양을 돌려준다.
    public int fillGp(int amount, int capacity) {
        int room = Math.max(0, capacity - this.storedGp);
        int actual = Math.min(amount, room);
        this.storedGp += actual;
        return actual;
    }

    // 이 건물에서 뺄 수 있는 만큼만 빼고, 실제로 뺀 양을 돌려준다.
    public int drainGp(int amount) {
        int actual = Math.min(amount, this.storedGp);
        this.storedGp -= actual;
        return actual;
    }

    public void addStoredFood(int amount) {
        this.storedFood += amount;
    }

    public int fillFood(int amount, int capacity) {
        int room = Math.max(0, capacity - this.storedFood);
        int actual = Math.min(amount, room);
        this.storedFood += actual;
        return actual;
    }

    public int drainFood(int amount) {
        int actual = Math.min(amount, this.storedFood);
        this.storedFood -= actual;
        return actual;
    }

    public LocalDateTime storedAt() {
        return LocalDateTime.now();
    }
}
