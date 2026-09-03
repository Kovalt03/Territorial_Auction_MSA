package com.territorial.auction.domain.admin.client;

import com.territorial.auction.domain.admin.dto.AdminBuildingTypeCatalogResponse;
import com.territorial.auction.domain.admin.dto.AdminBuildingTypeCatalogResponse.BuildingTypeInfo;
import com.territorial.auction.domain.admin.dto.AdminCreateBuildingTypeRequest;
import com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest.LevelSpecValues;
import com.territorial.auction.domain.admin.dto.AdminUnitLevelSpecsRequest.UnitLevelValues;
import com.territorial.auction.domain.admin.dto.AdminUnitTypeResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateBuildingTypeRequest;
import com.territorial.auction.domain.admin.dto.AdminUpdateUnitTypeRequest;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CombatAdminClientImpl implements CombatAdminClient {
    private static final String ROOT = "/internal/admin/combat";
    private final RestClient restClient;

    public CombatAdminClientImpl(
            RestClient.Builder builder,
            @Value("${combat-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .defaultStatusHandler(
                                status -> status.is4xxClientError(),
                                CombatAdminClientImpl::handleClientError)
                        .build();
    }

    @Override
    public AdminBuildingTypeCatalogResponse getBuildingTypes() {
        return restClient
                .get()
                .uri(ROOT + "/building-types")
                .retrieve()
                .body(AdminBuildingTypeCatalogResponse.class);
    }

    @Override
    public BuildingTypeInfo createBuildingType(AdminCreateBuildingTypeRequest request) {
        return restClient
                .post()
                .uri(ROOT + "/building-types")
                .body(request)
                .retrieve()
                .body(BuildingTypeInfo.class);
    }

    @Override
    public BuildingTypeInfo updateBuildingType(Long id, AdminUpdateBuildingTypeRequest request) {
        return restClient
                .patch()
                .uri(ROOT + "/building-types/{id}", id)
                .body(request)
                .retrieve()
                .body(BuildingTypeInfo.class);
    }

    @Override
    public String deleteBuildingType(Long id) {
        return restClient
                .delete()
                .uri(ROOT + "/building-types/{id}", id)
                .retrieve()
                .body(String.class);
    }

    @Override
    public Map<Integer, LevelSpecValues> getBuildingLevelSpecs(Long id) {
        return restClient
                .get()
                .uri(ROOT + "/building-types/{id}/level-specs", id)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public Map<Integer, LevelSpecValues> updateBuildingLevelSpecs(
            Long id, Map<Integer, LevelSpecValues> specs) {
        return restClient
                .patch()
                .uri(ROOT + "/building-types/{id}/level-specs", id)
                .body(Map.of("specs", specs))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public Map<Integer, Integer> getCastleLimits(Long id) {
        return restClient
                .get()
                .uri(ROOT + "/building-types/{id}/castle-limits", id)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public Map<Integer, Integer> updateCastleLimits(Long id, Map<Integer, Integer> limits) {
        return restClient
                .patch()
                .uri(ROOT + "/building-types/{id}/castle-limits", id)
                .body(Map.of("limits", limits))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<AdminUnitTypeResponse> getUnitTypes() {
        return restClient
                .get()
                .uri(ROOT + "/unit-types")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public AdminUnitTypeResponse updateUnitType(Long id, AdminUpdateUnitTypeRequest request) {
        return restClient
                .patch()
                .uri(ROOT + "/unit-types/{id}", id)
                .body(request)
                .retrieve()
                .body(AdminUnitTypeResponse.class);
    }

    @Override
    public Map<Integer, UnitLevelValues> getUnitLevelSpecs(Long id) {
        return restClient
                .get()
                .uri(ROOT + "/unit-types/{id}/level-specs", id)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public Map<Integer, UnitLevelValues> updateUnitLevelSpecs(
            Long id, Map<Integer, UnitLevelValues> specs) {
        return restClient
                .patch()
                .uri(ROOT + "/unit-types/{id}/level-specs", id)
                .body(Map.of("specs", specs))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public long getTotalStoredGp() {
        Long value = restClient.get().uri(ROOT + "/resources/total-gp").retrieve().body(Long.class);
        return value != null ? value : 0L;
    }

    @Override
    public UserResourceSnapshot getUserResources(Long userId, List<Long> territoryIds) {
        return restClient
                .get()
                .uri(
                        builder ->
                                builder.path(ROOT + "/users/{userId}/resources")
                                        .queryParam("territoryIds", territoryIds)
                                        .build(userId))
                .retrieve()
                .body(UserResourceSnapshot.class);
    }

    @Override
    public int adjustGp(Long userId, int delta, String commandKey) {
        GpBalanceView response =
                restClient
                        .post()
                        .uri(ROOT + "/resources/gp-adjustments")
                        .body(new AdjustGpRequest(userId, delta, commandKey))
                        .retrieve()
                        .body(GpBalanceView.class);
        return response != null ? response.availableGp() : 0;
    }

    private record AdjustGpRequest(Long userId, int delta, String commandKey) {}

    private record GpBalanceView(int availableGp) {}

    private static void handleClientError(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (body.contains(errorCode.getMessage())) {
                throw new CustomException(errorCode);
            }
        }
        throw new CustomException(ErrorCode.INVALID_INPUT);
    }
}
