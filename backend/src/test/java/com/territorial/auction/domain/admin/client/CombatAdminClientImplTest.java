package com.territorial.auction.domain.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CombatAdminClientImplTest {
    private CombatAdminClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CombatAdminClientImpl(builder, "http://combat", "test-secret");
    }

    @Test
    void readsUserResourcesWithInternalSecret() {
        server.expect(
                        requestTo(
                                "http://combat/internal/admin/combat/users/7/resources?territoryIds=10&territoryIds=11"))
                .andExpect(header("X-Internal-Service-Token", "test-secret"))
                .andRespond(
                        withSuccess(
                                "{\"availableGp\":120,\"availableFood\":45}",
                                MediaType.APPLICATION_JSON));

        var result = client.getUserResources(7L, List.of(10L, 11L));

        assertThat(result.availableGp()).isEqualTo(120);
        assertThat(result.availableFood()).isEqualTo(45);
        server.verify();
    }

    @Test
    void mapsCombatBusinessErrorBackToMonolithErrorCode() {
        server.expect(requestTo("http://combat/internal/admin/combat/building-types/3"))
                .andRespond(
                        withStatus(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"success\":false,\"message\":\"이미 배치된 건물이 있어 삭제할 수 없습니다.\",\"data\":null}"));

        assertThatThrownBy(() -> client.deleteBuildingType(3L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUILDING_TYPE_IN_USE);
        server.verify();
    }
}
