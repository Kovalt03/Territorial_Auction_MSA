package com.territorial.load;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * 우선순위 3 — combat 조회 부하 (금고·유닛). 게이트웨이(:8090) 경유, 인증 필요.
 *
 * <p>combat write(생산·연구·구매)는 병영·연구소·구매가능 건물 등 bootstrap(섬+성+빈금고) 이상의 상태 시딩이 필요해
 * 별도 combat 시더 전까지 보류. 이 시나리오는 combat-service를 부하로 exercise하는 조회 경로.
 *
 * <p>파라미터(-D): users ramp hold baseUrl. 토큰은 tokens.csv feeder.
 */
public class CombatReadSimulation extends Simulation {

  private static int prop(String k, int def) {
    String v = System.getProperty(k);
    return v == null ? def : Integer.parseInt(v);
  }

  private final int users = prop("users", 100);
  private final int ramp = prop("ramp", 30);
  private final int hold = prop("hold", 120);
  private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8090");

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl).acceptHeader("application/json");

  private final ScenarioBuilder scn =
      scenario("combat-read")
          .feed(csv("tokens.csv").random())
          .exec(
              http("vault")
                  .get("/api/v1/global-vault")
                  .header("Authorization", "Bearer #{token}")
                  .check(status().is(200)))
          .pause(Duration.ofMillis(50), Duration.ofMillis(200))
          .exec(
              http("units")
                  .get("/api/v1/military/units")
                  .header("Authorization", "Bearer #{token}")
                  .check(status().is(200)));

  {
    setUp(
            scn.injectClosed(
                rampConcurrentUsers(0).to(users).during(Duration.ofSeconds(ramp)),
                constantConcurrentUsers(users).during(Duration.ofSeconds(hold))))
        .protocols(httpProtocol)
        .assertions(global().failedRequests().percent().lt(1.0));
  }
}
