package com.territorial.combat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SeasonBenefitClientAdapterTest {

    private SeasonBenefitClientAdapter client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SeasonBenefitClientAdapter(builder, "http://monolith", "secret");
    }

    @Test
    void fetchesActiveCombatBenefit() {
        server.expect(requestTo("http://monolith/internal/seasons/users/1/combat-benefit"))
                .andExpect(header("X-Internal-Service-Token", "secret"))
                .andRespond(
                        withSuccess(
                                "{\"buildTimeReductionPct\":20,\"extraBuilders\":1}",
                                MediaType.APPLICATION_JSON));

        var benefit = client.findActiveBenefit(1L);

        assertThat(benefit.buildTimeReductionPct()).isEqualTo(20);
        assertThat(benefit.extraBuilders()).isEqualTo(1);
        server.verify();
    }
}
