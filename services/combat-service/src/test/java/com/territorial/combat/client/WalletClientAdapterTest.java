package com.territorial.combat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletClientAdapterTest {

    private WalletClientAdapter client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WalletClientAdapter(builder, new ObjectMapper(), "http://user", "secret");
    }

    @Test
    void spendsApThroughExistingIdempotentContract() {
        server.expect(requestTo("http://user/internal/wallets/spend"))
                .andExpect(method(POST))
                .andExpect(header("X-Internal-Service-Token", "secret"))
                .andRespond(
                        withSuccess(
                                "{\"availableAp\":700,\"lockedAp\":0}",
                                MediaType.APPLICATION_JSON));

        assertThat(client.spend(1L, 300, "BUILDING:1").availableAp()).isEqualTo(700);
        server.verify();
    }

    @Test
    void distinguishesCommandConflictFromInsufficientAp() {
        server.expect(requestTo("http://user/internal/wallets/spend"))
                .andRespond(
                        withStatus(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"message\":\"동일한 지갑 명령 키에 다른 요청이 전달되었습니다.\"}"));

        assertThatThrownBy(() -> client.spend(1L, 300, "BUILDING:1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WALLET_COMMAND_CONFLICT);
        server.verify();
    }
}
