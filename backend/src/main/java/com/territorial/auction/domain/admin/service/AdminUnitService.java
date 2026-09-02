package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.client.CombatAdminClient;
import com.territorial.auction.domain.admin.dto.AdminUnitLevelSpecsRequest.UnitLevelValues;
import com.territorial.auction.domain.admin.dto.AdminUnitTypeResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateUnitTypeRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUnitService {
    private final CombatAdminClient combatAdminClient;
    private final AdminAuditLogger adminAuditLogger;

    public List<AdminUnitTypeResponse> getUnitTypes() {
        return combatAdminClient.getUnitTypes();
    }

    @Transactional
    public AdminUnitTypeResponse update(Long adminId, Long id, AdminUpdateUnitTypeRequest request) {
        AdminUnitTypeResponse result = combatAdminClient.updateUnitType(id, request);
        audit(adminId, "UNIT_TYPE_UPDATE", id, result.name());
        return result;
    }

    public Map<Integer, UnitLevelValues> getLevelSpecs(Long id) {
        return combatAdminClient.getUnitLevelSpecs(id);
    }

    @Transactional
    public Map<Integer, UnitLevelValues> updateLevelSpecs(
            Long adminId, Long id, Map<Integer, UnitLevelValues> specs) {
        Map<Integer, UnitLevelValues> result = combatAdminClient.updateUnitLevelSpecs(id, specs);
        audit(adminId, "UNIT_LEVEL_SPEC_UPDATE", id, null);
        return result;
    }

    private void audit(Long adminId, String action, Long id, String name) {
        adminAuditLogger.record(
                adminId, action, "UNIT_TYPE", id, name != null ? Map.of("name", name) : Map.of());
    }
}
