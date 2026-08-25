package com.territorial.auction;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public class AuctionSmokeSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableWarmUp();

    private final ScenarioBuilder scenario =
            scenario("auction-smoke")
                    .feed(csv("tokens.csv").circular())
                    .exec(
                            http("active-auction-list")
                                    .get("/api/v1/auctions?status=BIDDING&size=20")
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
                                int minimumByPercent = (int) Math.ceil(currentPrice * 1.05);
                                return session.set(
                                        "bidAmount", Math.max(minimumByPercent, currentPrice + 10));
                            })
                    .exec(
                            http("place-bid")
                                    .post("/api/v1/auctions/#{auctionId}/bids")
                                    .header("Authorization", "Bearer #{token}")
                                    .body(StringBody("{\"bidAmount\": #{bidAmount}}"))
                                    .check(status().is(200)));

    public AuctionSmokeSimulation() {
        setUp(scenario.injectOpen(rampUsers(5).during(10)))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(99).lt(1_000),
                        global().failedRequests().percent().lt(1.0));
    }
}
