package com.territorial.auction.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletClientImplTest {

    private WalletClientImpl walletClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        walletClient =
                new WalletClientImpl(builder, new ObjectMapper(), "http://user", "test-secret");
    }

    @Test
    void commandFingerprintConflictIsNotReportedAsInsufficientBalance() {
        server.expect(requestTo("http://user/internal/wallets/bid-escrow"))
                .andRespond(
                        withStatus(CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"success\":false,\"message\":\"동일한 지갑 명령 키에 다른 요청이 전달되었습니다.\",\"data\":null}"));

        assertThatThrownBy(
                        () ->
                                walletClient.bidEscrow(
                                        new BidEscrowRequest(10L, 3L, 1000, null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WALLET_COMMAND_CONFLICT);
        server.verify();
    }

    @Test
    void balanceConflictRemainsInsufficientAp() {
        server.expect(requestTo("http://user/internal/wallets/consume-locked"))
                .andRespond(
                        withStatus(CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"success\":false,\"message\":\"AP 잔액이 부족합니다.\",\"data\":null}"));

        assertThatThrownBy(() -> walletClient.consumeLocked(3L, 1000, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        server.verify();
    }
}
