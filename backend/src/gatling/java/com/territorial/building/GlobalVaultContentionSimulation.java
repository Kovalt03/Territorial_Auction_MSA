package com.territorial.building;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class GlobalVaultContentionSimulation extends Simulation {

    private final int users = Integer.getInteger("users", 100);
    private final long firstTerritoryId = Long.getLong("firstTerritoryId", 6L);
    private final long secondTerritoryId = Long.getLong("secondTerritoryId", 1021L);

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableWarmUp();

    private Iterator<Map<String, Object>> hotUserFeeder() {
        String token = csv("tokens.csv").readRecords().get(0).get("token").toString();
        AtomicInteger sequence = new AtomicInteger();
        return Stream.generate(
                        () ->
                                Map.<String, Object>of(
                                        "token",
                                        token,
                                        "territoryId",
                                        sequence.getAndIncrement() % 2 == 0
                                                ? firstTerritoryId
                                                : secondTerritoryId))
                .iterator();
    }

    private final ScenarioBuilder scenario =
            scenario("global-vault-contention")
                    .feed(this::hotUserFeeder)
                    .exec(
                            http("transfer-under-contention")
                                    .post("/api/v1/global-vault/transfer")
                                    .header("Authorization", "Bearer #{token}")
                                    .body(
                                            StringBody(
                                                    "{\"direction\":\"TO_VAULT\",\"sourceTerritoryId\":#{territoryId},\"amount\":1}"))
                                    // 한 요청만 성공하고, 같은 금고 잠금을 기다린 나머지는 쿨다운으로 거절돼야 한다.
                                    .check(status().in(200, 429)));

    public GlobalVaultContentionSimulation() {
        setUp(scenario.injectOpen(atOnceUsers(users)))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        details("transfer-under-contention")
                                .responseTime()
                                .percentile(95.0)
                                .lt(1000));
    }
}
