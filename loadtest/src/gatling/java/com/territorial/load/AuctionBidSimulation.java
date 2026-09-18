package com.territorial.load;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 우선순위 1 — 경매 입찰 부하. 게이트웨이(:8090) 경유 end-to-end.
 *
 * <p>분산락(auction:lock:{id})이 걸린 고경합 쓰기 + auction→user(지갑 에스크로) 동기 호출이 엮인 대표 경로. 사전 덤프한
 * 토큰(tokens.csv)을 feeder로 주입 — 로그인 병목 착시 제거. 4xx(입찰가 부족·종료)·409(동시입찰)는 정상, 5xx만 실패로 집계.
 *
 * <p>파라미터(-D): users(동시 VU) ramp(초) hold(초) auctionMin/auctionMax(경매 ID 범위) baseUrl.
 * auctionMin==auctionMax 이면 단일 경매 경합(락 직렬화 상한), 범위면 분산 입찰(락 없는 상한).
 */
public class AuctionBidSimulation extends Simulation {

  private static int prop(String k, int def) {
    String v = System.getProperty(k);
    return v == null ? def : Integer.parseInt(v);
  }

  private final int users = prop("users", 20);
  private final int ramp = prop("ramp", 30);
  private final int hold = prop("hold", 60);
  private final int auctionMin = prop("auctionMin", 1);
  private final int auctionMax = prop("auctionMax", 200);
  private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8090");

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl)
          .contentTypeHeader("application/json")
          .acceptHeader("application/json");

  private final ScenarioBuilder scn =
      scenario("auction-bid")
          .feed(csv("tokens.csv").random())
          .exec(
              session -> {
                int aid = ThreadLocalRandom.current().nextInt(auctionMin, auctionMax + 1);
                // 현재가를 모르므로 크게 랜덤 — 일부는 성공, 일부는 상회 실패(400). 경합 상황 재현.
                int bid = ThreadLocalRandom.current().nextInt(1_000, 100_000_000);
                return session.set("auctionId", aid).set("bid", bid);
              })
          .exec(
              http("place-bid")
                  .post("/api/v1/auctions/#{auctionId}/bids")
                  .header("Authorization", "Bearer #{token}")
                  .body(StringBody("{\"bidAmount\": #{bid}}"))
                  .check(status().in(200, 400, 409)));

  {
    setUp(
            scn.injectClosed(
                rampConcurrentUsers(0).to(users).during(Duration.ofSeconds(ramp)),
                constantConcurrentUsers(users).during(Duration.ofSeconds(hold))))
        .protocols(httpProtocol)
        .assertions(global().failedRequests().percent().lt(1.0));
  }
}
