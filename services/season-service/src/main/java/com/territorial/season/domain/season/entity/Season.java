package com.territorial.season.domain.season.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "seasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer seasonNumber;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column private LocalDateTime endedAt;

    @Column private LocalDateTime processedAt;

    @Builder
    public Season(Integer seasonNumber, LocalDateTime startedAt, LocalDateTime endedAt) {
        this.seasonNumber = seasonNumber;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public void markProcessed() {
        this.processedAt = LocalDateTime.now();
    }

    // 관리자 즉시 종료: endedAt을 현재로 설정 → SeasonEndScheduler가 정산 처리한다.
    public void endNow(LocalDateTime now) {
        this.endedAt = now;
    }
}
