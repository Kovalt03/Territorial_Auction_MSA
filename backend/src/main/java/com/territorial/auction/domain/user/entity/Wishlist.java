package com.territorial.auction.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "wishlists",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "territory_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // territory는 map-service 소유라 FK 없이 식별자만 보관한다(공유 커널 map 추출).
    @Column(name = "territory_id", nullable = false)
    private Long territoryId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Wishlist(User user, Long territoryId) {
        this.user = user;
        this.territoryId = territoryId;
    }
}
