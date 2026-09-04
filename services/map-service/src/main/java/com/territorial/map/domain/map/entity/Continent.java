package com.territorial.map.domain.map.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "continents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Continent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    private String themeColor;

    @Column(length = 50)
    private String displayName;

    @Column(length = 2)
    private String grade;

    @Column private Integer minTrophyRequired;

    @Column(length = 100)
    private String description;

    @Builder
    public Continent(
            String name,
            String themeColor,
            String displayName,
            String grade,
            Integer minTrophyRequired,
            String description) {
        this.name = name;
        this.themeColor = themeColor;
        this.displayName = displayName;
        this.grade = grade;
        this.minTrophyRequired = minTrophyRequired;
        this.description = description;
    }

    public void updateDisplayData(
            String displayName, String grade, Integer minTrophyRequired, String description) {
        this.displayName = displayName;
        this.grade = grade;
        this.minTrophyRequired = minTrophyRequired;
        this.description = description;
    }
}
