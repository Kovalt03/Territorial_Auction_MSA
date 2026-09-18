package com.territorial.load;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 우선순위 2 — 맵 조회 부하. 게이트웨이(:8090) 경유, 비인증 공개 경로.
 *
 * <p>최대 트래픽 경로: 전 영토(50×50) 직렬화 비용 + 영토 상세. `/grid`는 ETag 조건부 GET이라 If-None-Match 없이
 * 매번 전체 200을 받아 직렬화 상한을 본다. 토큰 불필요.
 *
 * <p>파라미터(-D): users ramp hold territoryMax baseUrl.
 */
public class MapReadSimulation extends Simulation {

  private static int prop(String k, int def) {
    String v = System.getProperty(k);
    return v == null ? def : Integer.parseInt(v);
  }

  private final int users = prop("users", 100);
  private final int ramp = prop("ramp", 30);
  private final int hold = prop("hold", 120);
  private final int territoryMax = prop("territoryMax", 2400);
  // territory-detail은 combat-service를 동기 호출하므로 combat이 서브셋에 있을 때만 켠다(기본 off).
  private final boolean withDetail = Boolean.parseBoolean(System.getProperty("withDetail", "false"));
  private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8090");

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl).acceptHeader("application/json");

  private final ScenarioBuilder scn =
      scenario("map-read")
          .exec(http("grid").get("/api/v1/map/grid").check(status().is(200)))
          .doIf(session -> withDetail)
          .then(
              pause(Duration.ofMillis(50), Duration.ofMillis(200))
                  .exec(
                      session ->
                          session.set(
                              "tid", ThreadLocalRandom.current().nextInt(1, territoryMax + 1)))
                  .exec(
                      http("territory-detail")
                          .get("/api/v1/map/territories/#{tid}")
                          .check(status().in(200, 404))));

  {
    setUp(
            scn.injectClosed(
                rampConcurrentUsers(0).to(users).during(Duration.ofSeconds(ramp)),
                constantConcurrentUsers(users).during(Duration.ofSeconds(hold))))
        .protocols(httpProtocol)
        .assertions(global().failedRequests().percent().lt(1.0));
  }
}
