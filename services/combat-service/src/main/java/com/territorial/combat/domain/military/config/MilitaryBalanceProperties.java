package com.territorial.combat.domain.military.config;

import com.territorial.combat.domain.military.MilitaryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MilitaryBalanceProperties {

    private final int castleCapacity;
    private final int residenceCapacity;
    private final int towerCapacity;
    private final int wallCapacity;

    public MilitaryBalanceProperties(
            @Value("${military.balance.garrison.castle:" + MilitaryPolicy.GARRISON_CAP_CASTLE + "}")
                    int castleCapacity,
            @Value(
                            "${military.balance.garrison.residence:"
                                    + MilitaryPolicy.GARRISON_CAP_RESIDENCE
                                    + "}")
                    int residenceCapacity,
            @Value("${military.balance.garrison.tower:" + MilitaryPolicy.GARRISON_CAP_TOWER + "}")
                    int towerCapacity,
            @Value("${military.balance.garrison.wall:" + MilitaryPolicy.GARRISON_CAP_WALL + "}")
                    int wallCapacity) {
        this.castleCapacity = castleCapacity;
        this.residenceCapacity = residenceCapacity;
        this.towerCapacity = towerCapacity;
        this.wallCapacity = wallCapacity;
    }

    public int garrisonCapacity(String buildingType) {
        return switch (buildingType) {
            case "CASTLE" -> castleCapacity;
            case "RESIDENCE" -> residenceCapacity;
            case "TOWER" -> towerCapacity;
            case "WALL" -> wallCapacity;
            default -> 0;
        };
    }
}
