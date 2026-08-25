package com.territorial.auction;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class AuctionLoadSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableWarmUp();

    private final ScenarioBuilder scenario =
            scenario("auction-load")
                    .feed(csv("tokens.csv").circular())
                    .during(Duration.ofMinutes(10))
                    .on(
                            exec(session ->
                                            session.set(
                                                    "auctionPage",
                                                    ThreadLocalRandom.current().nextInt(10)))
                                    .exec(
                                            http("active-auction-list")
                                                    .get(
                                                            "/api/v1/auctions?status=BIDDING&size=20&page=#{auctionPage}")
                                                    .check(status().is(200))
                                                    .check(
                                                            jsonPath("$.data.auctions[0].auctionId")
                                                                    .saveAs("auctionId"))
                                                    .check(
                                                            jsonPath(
                                                                            "$.data.auctions[0].currentPrice")
                                                                    .ofInt()
                                                                    .saveAs("currentPrice")))
                                    .exec(
                                            session -> {
                                                int currentPrice = session.getInt("currentPrice");
                                                int minimumByPercent =
                                                        (int) Math.ceil(currentPrice * 1.05);
                                                return session.set(
                                                        "bidAmount",
                                                        Math.max(
                                                                minimumByPercent,
                                                                currentPrice + 10));
                                            })
                                    .exec(
                                            http("place-bid")
                                                    .post("/api/v1/auctions/#{auctionId}/bids")
                                                    .header("Authorization", "Bearer #{token}")
                                                    .body(
                                                            StringBody(
                                                                    "{\"bidAmount\": #{bidAmount}}"))
                                                    .check(status().in(200, 400, 409)))
                                    .pause(Duration.ofSeconds(3)));

    public AuctionLoadSimulation() {
        setUp(scenario.injectOpen(rampUsers(100).during(60)))
                .protocols(httpProtocol)
                .assertions(
                        details("place-bid").responseTime().percentile(95).lt(500),
                        global().responseTime().percentile(99).lt(1_000),
                        global().failedRequests().percent().lt(1.0));
    }
}
