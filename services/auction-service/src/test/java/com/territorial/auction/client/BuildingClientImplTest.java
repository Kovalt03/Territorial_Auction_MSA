package com.territorial.auction.client;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BuildingClientImplTest {
    private BuildingClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BuildingClientImpl(builder, "http://combat", "test-secret");
    }

    @Test
    void createsInitialCastleThroughSecuredCombatContract() {
        server.expect(requestTo("http://combat/internal/buildings/initial-castle"))
                .andExpect(header("X-Internal-Service-Token", "test-secret"))
                .andExpect(jsonPath("$.territoryId").value(7))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        client.createInitialCastle(7L);

        server.verify();
    }
}
