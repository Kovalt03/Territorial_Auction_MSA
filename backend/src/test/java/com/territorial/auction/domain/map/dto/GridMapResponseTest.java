package com.territorial.auction.domain.map.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class GridMapResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialize_usesPublicApiFieldNames() throws Exception {
        GridMapResponse.GridTerritoryDto territory =
                new GridMapResponse.GridTerritoryDto(
                        1L, 0, 0, 2L, "owner", "#FF4444", "B", "BIDDING", true, 3L, 12);
        GridMapResponse response = new GridMapResponse(50, List.of(territory));

        JsonNode json = objectMapper.valueToTree(response);
        JsonNode serializedTerritory = json.path("territories").get(0);

        assertThat(json.path("mapSize").asInt()).isEqualTo(50);
        assertThat(serializedTerritory.path("territoryId").asLong()).isEqualTo(1L);
        assertThat(serializedTerritory.path("currentColor").asText()).isEqualTo("#FF4444");
        assertThat(serializedTerritory.path("hasActiveAuction").asBoolean()).isTrue();
        assertThat(json.has("gridSize")).isFalse();
        assertThat(serializedTerritory.has("id")).isFalse();
        assertThat(serializedTerritory.has("color")).isFalse();
        assertThat(serializedTerritory.has("isAuctioning")).isFalse();
    }
}
