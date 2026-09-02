package com.territorial.combat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TerritoryClientTest {

    private TerritoryClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TerritoryClient(builder, "http://monolith", "test-secret");
    }

    @Test
    void fetchesSharedCombatContextWithInternalSecret() {
        server.expect(requestTo("http://monolith/internal/territories/10/combat-context"))
                .andExpect(header("X-Internal-Service-Token", "test-secret"))
                .andRespond(
                        withSuccess(
                                """
                                {"territoryId":10,"ownerId":2,"coordX":4,"coordY":5,
                                 "status":"OCCUPIED","protectedUntil":null,
                                 "gridSize":12,"zone1Radius":2,"zone2Radius":4}
                                """,
                                MediaType.APPLICATION_JSON));

        var response = client.findById(10L);

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().ownerId()).isEqualTo(2L);
        assertThat(response.orElseThrow().gridSize()).isEqualTo(12);
        server.verify();
    }

    @Test
    void fetchesOwnedContexts() {
        server.expect(requestTo("http://monolith/internal/territories/owners/2/combat-contexts"))
                .andRespond(
                        withSuccess(
                                """
                                [{"territoryId":10,"ownerId":2,"coordX":4,"coordY":5,
                                  "status":"OCCUPIED","protectedUntil":null,
                                  "gridSize":12,"zone1Radius":2,"zone2Radius":4}]
                                """,
                                MediaType.APPLICATION_JSON));

        List<TerritoryClient.TerritoryCombatContextResponse> response =
                client.findOwnedByUserId(2L);

        assertThat(response).extracting("territoryId").containsExactly(10L);
        server.verify();
    }
}
