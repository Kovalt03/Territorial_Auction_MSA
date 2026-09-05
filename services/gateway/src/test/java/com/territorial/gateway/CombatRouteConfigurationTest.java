package com.territorial.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest(
        properties = {
            "jwt.secret=test-secret-key-that-is-long-enough-for-hs256",
            "INTERNAL_API_SECRET=gateway-test-secret",
            "COMBAT_SERVICE_URL=http://combat-test:8080"
        })
class CombatRouteConfigurationTest {

    @Autowired private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void combatRouteInjectsGatewayToken() {
        var routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();
        RouteDefinition combat =
                routes.stream()
                        .filter(route -> route.getId().equals("combat-service"))
                        .findFirst()
                        .orElseThrow();

        assertThat(combat.getUri()).isEqualTo(URI.create("http://combat-test:8080"));
        assertThat(combat.getPredicates().get(0).getArgs().values())
                .anyMatch(value -> value.contains("/api/v1/military/**"))
                .anyMatch(value -> value.contains("/api/v1/map/territories/*/buildings"));
        assertThat(combat.getFilters().get(0).getArgs().values())
                .contains("X-Gateway-Service-Token", "gateway-test-secret");
    }
}
