package com.territorial.combat.domain.building.config;

import com.territorial.combat.domain.building.BuildingPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BuildingBalanceProperties {

    private final int repairGpPerHp;

    public BuildingBalanceProperties(
            @Value("${building.balance.repair-gp-per-hp:" + BuildingPolicy.REPAIR_GP_PER_HP + "}")
                    int repairGpPerHp) {
        this.repairGpPerHp = repairGpPerHp;
    }

    public int repairGpPerHp() {
        return repairGpPerHp;
    }
}
