package com.territorial.auction;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

public class AuctionSpikeSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableWarmUp();

    private ChainBuilder bidOnce(String requestPrefix) {
        return feed(csv("tokens.csv").circular())
                .exec(
                        http(requestPrefix + "-auction-list")
                                .get("/api/v1/auctions?status=BIDDING&size=1")
                                .check(status().is(200))
                                .check(jsonPath("$.data.auctions[0].auctionId").saveAs("auctionId"))
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
                        http(requestPrefix + "-place-bid")
                                .post("/api/v1/auctions/#{auctionId}/bids")
                                .header("Authorization", "Bearer #{token}")
                                .body(StringBody("{\"bidAmount\": #{bidAmount}}"))
                                .check(status().in(200, 400)));
    }

    private final ScenarioBuilder steadyScenario =
            scenario("auction-spike-steady")
                    .during(Duration.ofMinutes(3))
                    .on(exec(bidOnce("steady")).pause(Duration.ofSeconds(1)));

    private final ScenarioBuilder spikeScenario =
            scenario("auction-spike-burst").exec(bidOnce("spike"));

    private final ScenarioBuilder recoveryScenario =
            scenario("auction-spike-recovery").exec(bidOnce("recovery"));

    public AuctionSpikeSimulation() {
        setUp(
                        steadyScenario.injectClosed(
                                constantConcurrentUsers(10).during(Duration.ofMinutes(3))),
                        spikeScenario.injectOpen(
                                nothingFor(Duration.ofSeconds(60)), atOnceUsers(100)),
                        recoveryScenario.injectOpen(
                                nothingFor(Duration.ofSeconds(150)), atOnceUsers(10)))
                .protocols(httpProtocol)
                .assertions(
                        details("steady-auction-list").failedRequests().percent().is(0.0),
                        details("steady-place-bid").failedRequests().percent().lt(1.0),
                        details("spike-auction-list").failedRequests().percent().is(0.0),
                        details("spike-place-bid").failedRequests().percent().lt(1.0),
                        details("recovery-auction-list").failedRequests().percent().is(0.0),
                        details("recovery-place-bid").failedRequests().percent().is(0.0));
    }
}
