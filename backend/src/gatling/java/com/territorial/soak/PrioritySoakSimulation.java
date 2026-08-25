package com.territorial.soak;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class PrioritySoakSimulation extends Simulation {

    private final int mapUsers = Integer.getInteger("mapUsers", 30);
    private final int auctionUsers = Integer.getInteger("auctionUsers", 20);
    private final long durationSeconds = Long.getLong("durationSeconds", 3600L);
    private final long rampSeconds = Long.getLong("rampSeconds", 60L);

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableWarmUp();

    private final ScenarioBuilder mapScenario =
            scenario("soak-map")
                    .exec(
                            http("soak-initial-grid")
                                    .get("/api/v1/map/grid")
                                    .check(status().is(200))
                                    .check(
                                            jsonPath("$.data.territories[2499].territoryId")
                                                    .exists())
                                    .check(header("ETag").saveAs("gridEtag")))
                    .during(Duration.ofSeconds(durationSeconds))
                    .on(
                            exec(http("soak-full-grid")
                                            .get("/api/v1/map/grid")
                                            .header("If-None-Match", "#{gridEtag}")
                                            .check(status().in(200, 304))
                                            .check(header("ETag").saveAs("gridEtag")))
                                    .exec(
                                            session ->
                                                    session.set(
                                                            "territoryId",
                                                            ThreadLocalRandom.current()
                                                                    .nextLong(1, 2501)))
                                    .exec(
                                            http("soak-territory-detail")
                                                    .get("/api/v1/map/territories/#{territoryId}")
                                                    .check(status().is(200)))
                                    .pause(Duration.ofSeconds(5)));

    private final ScenarioBuilder auctionScenario =
            scenario("soak-auction")
                    .feed(csv("tokens.csv").circular())
                    .during(Duration.ofSeconds(durationSeconds))
                    .on(
                            exec(http("soak-active-auction-list")
                                            .get("/api/v1/auctions?status=BIDDING&size=20&page=0")
                                            .check(status().is(200))
                                            .check(
                                                    jsonPath("$.data.auctions[0].auctionId")
                                                            .saveAs("auctionId"))
                                            .check(
                                                    jsonPath("$.data.auctions[0].currentPrice")
                                                            .ofInt()
                                                            .saveAs("currentPrice")))
                                    .exec(
                                            session -> {
                                                int currentPrice = session.getInt("currentPrice");
                                                return session.set(
                                                        "bidAmount",
                                                        Math.max(
                                                                (int)
                                                                        Math.ceil(
                                                                                currentPrice
                                                                                        * 1.05),
                                                                currentPrice + 10));
                                            })
                                    .exec(
                                            http("soak-place-bid")
                                                    .post("/api/v1/auctions/#{auctionId}/bids")
                                                    .header("Authorization", "Bearer #{token}")
                                                    .body(
                                                            StringBody(
                                                                    "{\"bidAmount\": #{bidAmount}}"))
                                                    .check(status().in(200, 400, 409)))
                                    .pause(Duration.ofSeconds(5)));

    public PrioritySoakSimulation() {
        setUp(
                        mapScenario.injectClosed(
                                rampConcurrentUsers(1)
                                        .to(mapUsers)
                                        .during(Duration.ofSeconds(rampSeconds))),
                        auctionScenario.injectClosed(
                                rampConcurrentUsers(1)
                                        .to(auctionUsers)
                                        .during(Duration.ofSeconds(rampSeconds))))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        details("soak-territory-detail").responseTime().percentile(95).lt(200),
                        details("soak-place-bid").responseTime().percentile(95).lt(500),
                        global().responseTime().percentile(99).lt(1_000));
    }
}
