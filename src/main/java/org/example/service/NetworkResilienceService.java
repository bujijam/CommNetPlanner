package org.example.service;

import org.example.graph.Graph;
import org.example.model.Link;
import org.example.model.Node;
import org.example.model.NodeType;

import java.util.List;

public class NetworkResilienceService {

    private final ConnectivityService connectivityService;

    public NetworkResilienceService(ConnectivityService connectivityService) {
        this.connectivityService = connectivityService;
    }

    public ResilienceScore calculate(Graph graph) {
        List<Node> allNodes = List.copyOf(graph.nodes());
        List<Link> allLinks = List.copyOf(graph.links());

        long keyNodeCount = allNodes.stream()
                .filter(n -> n.getNodeType() == NodeType.COMMAND || n.getNodeType() == NodeType.HOSPITAL)
                .count();

        if (keyNodeCount == 0) {
            return new ResilienceScore(0, 0, 0, 0);
        }

        int commandStart = allNodes.stream()
                .filter(n -> n.getNodeType() == NodeType.COMMAND)
                .mapToInt(Node::getId)
                .findFirst()
                .orElse(allNodes.get(0).getId());

        List<List<Integer>> components = connectivityService.components(graph);
        java.util.Set<Integer> reachableFromCommand = components.stream()
                .filter(comp -> comp.contains(commandStart))
                .findFirst()
                .map(java.util.HashSet::new)
                .orElse(new java.util.HashSet<>());

        long connectedKeyNodes = allNodes.stream()
                .filter(n -> n.getNodeType() == NodeType.COMMAND || n.getNodeType() == NodeType.HOSPITAL)
                .filter(n -> reachableFromCommand.contains(n.getId()))
                .count();

        double linkReliability = allLinks.isEmpty() ? 1.0
                : 1.0 - allLinks.stream().mapToDouble(Link::getPredictedResistance).average().orElse(0.0);

        double connectivityRatio = (double) connectedKeyNodes / keyNodeCount;
        double structuralScore = allNodes.isEmpty() ? 0.0
                : Math.min(1.0, (double) allLinks.size() / allNodes.size());
        double composite = 0.4 * connectivityRatio + 0.3 * linkReliability + 0.3 * structuralScore;

        return new ResilienceScore(composite * 100, (int) connectedKeyNodes, (int) keyNodeCount, linkReliability);
    }
}
