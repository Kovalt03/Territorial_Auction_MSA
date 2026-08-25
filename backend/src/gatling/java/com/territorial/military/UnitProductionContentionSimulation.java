package com.territorial.military;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

public class UnitProductionContentionSimulation extends Simulation {

    private final int users = Integer.getInteger("users", 50);
    private final long islandId = Long.getLong("islandId", 1L);

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableWarmUp();

    private Iterator<Map<String, Object>> hotUserFeeder() {
        String token = csv("tokens.csv").readRecords().get(0).get("token").toString();
        return Stream.generate(() -> Map.<String, Object>of("token", token)).iterator();
    }

    private final ScenarioBuilder scenario =
            scenario("unit-production-contention")
                    .feed(this::hotUserFeeder)
                    .exec(
                            http("produce-unit-under-contention")
                                    .post("/api/v1/military/units")
                                    .header("Authorization", "Bearer #{token}")
                                    .body(
                                            StringBody(
                                                    "{\"unitTypeId\":1,\"quantity\":1,\"level\":1,\"locationId\":"
                                                            + islandId
                                                            + ",\"locationType\":\"ISLAND\"}"))
                                    .check(status().is(200)));

    public UnitProductionContentionSimulation() {
        setUp(scenario.injectOpen(atOnceUsers(users)))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        details("produce-unit-under-contention")
                                .responseTime()
                                .percentile(95.0)
                                .lt(500));
    }
}
