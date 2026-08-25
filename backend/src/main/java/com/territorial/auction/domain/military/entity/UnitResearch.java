package com.territorial.auction.domain.military.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정 단위 유닛 연구 상태. 유닛 종류별로 해금된 레벨을 보관한다. 연구소에서 다음 레벨을 연구하면 GP·시간을 들여 상한을 올린다. 기본 레벨 1은 항상 사용 가능(행이
 * 없으면 1로 간주).
 */
@Entity
@Table(
        name = "unit_research",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "unit_type_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitResearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitType unitType;

    /** 해금된(연구 완료) 레벨. 기본 1. */
    @Column(nullable = false)
    private Integer researchedLevel = 1;

    /** 진행 중 연구의 목표 레벨. NULL이면 진행 중 아님. */
    private Integer pendingLevel;

    /** 진행 중 연구의 완료 시각. 이 시각이 지나면 researchedLevel이 pendingLevel로 올라간다. */
    private LocalDateTime researchCompleteAt;

    @Builder
    public UnitResearch(User user, UnitType unitType, Integer researchedLevel) {
        this.user = user;
        this.unitType = unitType;
        this.researchedLevel = researchedLevel != null ? researchedLevel : 1;
    }

    public void startResearch(int targetLevel, LocalDateTime completeAt) {
        this.pendingLevel = targetLevel;
        this.researchCompleteAt = completeAt;
    }

    public boolean isResearching(LocalDateTime now) {
        return pendingLevel != null
                && researchCompleteAt != null
                && researchCompleteAt.isAfter(now);
    }

    /** 완료 시각이 지난 연구가 있으면 반영한다. 조회·생산 시 지연 정산으로 호출. */
    public void applyCompletionIfDue(LocalDateTime now) {
        if (pendingLevel != null
                && researchCompleteAt != null
                && !researchCompleteAt.isAfter(now)) {
            this.researchedLevel = pendingLevel;
            this.pendingLevel = null;
            this.researchCompleteAt = null;
        }
    }
}
