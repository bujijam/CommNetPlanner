package org.example.config;

import org.example.graph.Graph;
import org.example.persistence.GraphStore;
import org.example.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {

    @Bean
    public Graph graph() {
        return new Graph();
    }

    @Bean
    public ScenarioStore scenarioStore() {
        return new ScenarioStore();
    }

    @Bean
    public GraphStore graphStore() {
        return new GraphStore();
    }

    @Bean
    public ShortestPathService shortestPathService() {
        return new ShortestPathService();
    }

    @Bean
    public ConnectivityService connectivityService() {
        return new ConnectivityService();
    }

    @Bean
    public AugmentConnectivityService augmentConnectivityService() {
        return new AugmentConnectivityService();
    }

    @Bean
    public SteinerService steinerService() {
        return new SteinerService();
    }

    @Bean
    public DisasterSteinerService disasterSteinerService() {
        return new DisasterSteinerService();
    }

    @Bean
    public NetworkResilienceService networkResilienceService(ConnectivityService connectivityService) {
        return new NetworkResilienceService(connectivityService);
    }
}
