package com.territorial.auction.domain.social.entity;

import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interest_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "continent_id", nullable = false)
    private Continent continent;

    @Builder
    public InterestGroup(User user, Continent continent) {
        this.user = user;
        this.continent = continent;
    }
}
