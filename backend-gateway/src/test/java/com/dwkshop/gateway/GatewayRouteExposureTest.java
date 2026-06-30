package com.dwkshop.gateway;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRouteExposureTest {

    @Autowired RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void gatewayDoesNotExposeInternalServiceRoutesThatCouldBypassSharedSecret() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();
        assertThat(routes)
            .flatExtracting(route -> route.getPredicates().stream()
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .toList())
            .allSatisfy(patterns -> assertThat(patterns)
                .doesNotContain("/internal/")
                .isNotEqualTo("/**"));
        assertThat(routes).extracting(RouteDefinition::getId)
            .contains("auth-service", "product-service", "order-service", "aftersale-service");
        RouteDefinition productRoute = routes.stream()
            .filter(route -> "product-service".equals(route.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(productRoute.getPredicates().stream()
            .flatMap(predicate -> predicate.getArgs().values().stream())
            .toList())
            .contains("/admin/inventory-reconciliation", "/admin/inventory-reconciliation/**");
    }
}
