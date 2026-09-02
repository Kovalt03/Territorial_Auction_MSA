package com.territorial.combat.domain.military.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitType unitType;

    @Column(nullable = false)
    private Integer researchedLevel = 1;

    private Integer pendingLevel;
    private LocalDateTime researchCompleteAt;

    @Builder
    public UnitResearch(Long userId, UnitType unitType, Integer researchedLevel) {
        this.userId = userId;
        this.unitType = unitType;
        this.researchedLevel = researchedLevel != null ? researchedLevel : 1;
    }

    public void startResearch(int targetLevel, LocalDateTime completeAt) {
        pendingLevel = targetLevel;
        researchCompleteAt = completeAt;
    }

    public boolean isResearching(LocalDateTime now) {
        return pendingLevel != null
                && researchCompleteAt != null
                && researchCompleteAt.isAfter(now);
    }

    public void applyCompletionIfDue(LocalDateTime now) {
        if (pendingLevel != null
                && researchCompleteAt != null
                && !researchCompleteAt.isAfter(now)) {
            researchedLevel = pendingLevel;
            pendingLevel = null;
            researchCompleteAt = null;
        }
    }
}
