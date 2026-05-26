package org.example.service;

import org.example.graph.Graph;
import org.example.model.Link;
import org.example.model.Node;

import java.util.*;

public class ShortestPathService {
    private static final double INF = Double.MAX_VALUE / 4;

    public ShortestPathResult calculate(Graph graph, int sourceId) {
        if (graph.node(sourceId).isEmpty()) {
            throw new IllegalArgumentException("Source node not found: " + sourceId);
        }

        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();
        for (Node node : graph.nodes()) {
            dist.put(node.getId(), INF);
            prev.put(node.getId(), null);
        }
        dist.put(sourceId, 0.0);

        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(node -> node.distance));
        pq.offer(new NodeDistance(sourceId, 0.0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            if (current.distance > dist.get(current.nodeId)) {
                continue;
            }
            for (Map.Entry<Integer, Link> entry : graph.neighbors(current.nodeId).entrySet()) {
                int nextId = entry.getKey();
                Link link = entry.getValue();
                double weight = link.getBaseCost() * (1 + link.getPredictedResistance());
                double candidate = current.distance + weight;
                if (candidate < dist.getOrDefault(nextId, INF)) {
                    dist.put(nextId, candidate);
                    prev.put(nextId, current.nodeId);
                    pq.offer(new NodeDistance(nextId, candidate));
                }
            }
        }

        List<ShortestPathResult.PathEntry> entries = new ArrayList<>();
        for (Node node : graph.nodes()) {
            if (node.getId() == sourceId) {
                continue;
            }
            double distance = dist.getOrDefault(node.getId(), INF);
            if (distance >= INF) {
                entries.add(new ShortestPathResult.PathEntry(node.getId(), -1, false, List.of()));
                continue;
            }
            entries.add(new ShortestPathResult.PathEntry(node.getId(), distance, true, reconstructPath(prev, sourceId, node.getId())));
        }

        entries.sort(Comparator
                .comparing((ShortestPathResult.PathEntry e) -> !e.reachable())
                .thenComparingDouble(e -> e.reachable() ? e.distance() : Double.MAX_VALUE)
                .thenComparingInt(ShortestPathResult.PathEntry::targetId));

        return new ShortestPathResult(sourceId, entries);
    }

    private List<Integer> reconstructPath(Map<Integer, Integer> prev, int sourceId, int targetId) {
        List<Integer> path = new ArrayList<>();
        Integer cursor = targetId;
        while (cursor != null) {
            path.add(cursor);
            if (cursor == sourceId) {
                break;
            }
            cursor = prev.get(cursor);
        }
        List<Integer> reversed = new ArrayList<>();
        for (int i = path.size() - 1; i >= 0; i--) {
            reversed.add(path.get(i));
        }
        return reversed;
    }

    private record NodeDistance(int nodeId, double distance) {
    }
}
