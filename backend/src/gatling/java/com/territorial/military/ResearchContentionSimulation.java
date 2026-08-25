package com.territorial.military;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ResearchContentionSimulation extends Simulation {

    private final int users = Integer.getInteger("users", 20);

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
                                        "unitTypeId",
                                        sequence.getAndIncrement() % 2 == 0 ? 1 : 2))
                .iterator();
    }

    private final ScenarioBuilder scenario =
            scenario("research-contention")
                    .feed(this::hotUserFeeder)
                    .exec(
                            http("start-research-under-contention")
                                    .post("/api/v1/military/research/#{unitTypeId}")
                                    .header("Authorization", "Bearer #{token}")
                                    // 계정당 하나만 성공하고 나머지는 진행 중(409)이어야 한다.
                                    .check(status().in(200, 409)));

    public ResearchContentionSimulation() {
        setUp(scenario.injectOpen(atOnceUsers(users)))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        details("start-research-under-contention")
                                .responseTime()
                                .percentile(95.0)
                                .lt(500));
    }
}
