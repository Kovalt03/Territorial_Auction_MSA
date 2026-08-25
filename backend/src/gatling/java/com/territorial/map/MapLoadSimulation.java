package com.territorial.map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class MapLoadSimulation extends Simulation {

    private final int users = Integer.getInteger("users", 100);
    private final long durationSeconds = Long.getLong("durationSeconds", 600L);
    private final long rampSeconds = Long.getLong("rampSeconds", 60L);

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .disableWarmUp();

    private final ScenarioBuilder scenario =
            scenario("map-load")
                    .during(Duration.ofSeconds(durationSeconds))
                    .on(
                            exec(http("full-grid")
                                            .get("/api/v1/map/grid")
                                            .check(status().is(200))
                                            .check(
                                                    jsonPath("$.data.territories[2499].territoryId")
                                                            .exists()))
                                    .exec(
                                            session ->
                                                    session.set(
                                                            "territoryId",
                                                            ThreadLocalRandom.current()
                                                                    .nextLong(1, 2501)))
                                    .exec(
                                            http("territory-detail")
                                                    .get("/api/v1/map/territories/#{territoryId}")
                                                    .check(status().is(200)))
                                    .pause(Duration.ofSeconds(2)));

    public MapLoadSimulation() {
        setUp(
                        scenario.injectClosed(
                                rampConcurrentUsers(1)
                                        .to(users)
                                        .during(Duration.ofSeconds(rampSeconds))))
                .protocols(httpProtocol)
                .assertions(
                        details("full-grid").responseTime().percentile(95).lt(200),
                        details("territory-detail").responseTime().percentile(95).lt(200),
                        global().responseTime().percentile(99).lt(1_000),
                        global().failedRequests().percent().lt(1.0));
    }
}
