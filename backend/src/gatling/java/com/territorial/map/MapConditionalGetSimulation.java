package com.territorial.map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

public class MapConditionalGetSimulation extends Simulation {

    private final int users = Integer.getInteger("users", 100);
    private final long durationSeconds = Long.getLong("durationSeconds", 600L);
    private final long rampSeconds = Long.getLong("rampSeconds", 60L);

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .disableWarmUp();

    private final ScenarioBuilder scenario =
            scenario("map-conditional-get")
                    .exec(
                            http("initial-grid")
                                    .get("/api/v1/map/grid")
                                    .check(status().is(200))
                                    .check(header("ETag").saveAs("gridEtag")))
                    .during(Duration.ofSeconds(durationSeconds))
                    .on(
                            exec(http("conditional-grid")
                                            .get("/api/v1/map/grid")
                                            .header("If-None-Match", "#{gridEtag}")
                                            .check(status().in(200, 304))
                                            .check(header("ETag").saveAs("gridEtag")))
                                    .pause(Duration.ofSeconds(2)));

    public MapConditionalGetSimulation() {
        setUp(
                        scenario.injectClosed(
                                rampConcurrentUsers(1)
                                        .to(users)
                                        .during(Duration.ofSeconds(rampSeconds))))
                .protocols(httpProtocol)
                .assertions(
                        details("conditional-grid").responseTime().percentile(95).lt(200),
                        global().failedRequests().percent().lt(1.0));
    }
}
