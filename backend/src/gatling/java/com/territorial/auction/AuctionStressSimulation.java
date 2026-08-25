package com.territorial.auction;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public class AuctionStressSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableWarmUp();

    private final ScenarioBuilder scenario =
            scenario("auction-stress")
                    .feed(csv("tokens.csv").circular())
                    .exec(
                            http("active-auction-list")
                                    .get("/api/v1/auctions?status=BIDDING&size=1")
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
                            http("place-bid-contention")
                                    .post("/api/v1/auctions/#{auctionId}/bids")
                                    .header("Authorization", "Bearer #{token}")
                                    .body(StringBody("{\"bidAmount\": #{bidAmount}}"))
                                    // 400은 가격 경합/잔액 부족, 409는 분산락 획득 실패이므로 KO로 남긴다.
                                    .check(status().in(200, 400)));

    public AuctionStressSimulation() {
        setUp(
                        scenario.injectClosed(
                                incrementConcurrentUsers(50)
                                        .times(8)
                                        .eachLevelLasting(30)
                                        .separatedByRampsLasting(15)
                                        .startingFrom(50)))
                .protocols(httpProtocol)
                .assertions(details("active-auction-list").failedRequests().percent().is(0.0));
    }
}
