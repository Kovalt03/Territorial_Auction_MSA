package com.territorial.auction.domain.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuctionQueryClientImplTest {
    private AuctionQueryClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuctionQueryClientImpl(builder, "http://auction", "test-secret");
    }

    @Test
    void sendsInternalSecretForAdminQuery() {
        server.expect(requestTo("http://auction/internal/auctions/active-count"))
                .andExpect(header("X-Internal-Service-Token", "test-secret"))
                .andRespond(withSuccess("3", MediaType.APPLICATION_JSON));

        assertThat(client.countActiveAuctions()).isEqualTo(3);
        server.verify();
    }
}
