package org.example.service;

import org.example.graph.Graph;
import org.example.model.Link;
import org.example.model.LinkType;
import org.example.model.Node;
import org.example.util.GeoUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AugmentConnectivityService {
    private final ConnectivityService connectivityService = new ConnectivityService();

    public ConnectivityResult analyze(Graph graph) {
        List<List<Integer>> components = connectivityService.components(graph);
        if (components.size() <= 1) {
            return new ConnectivityResult(true, components.size(), components, List.of(), 0);
        }

        List<ComponentEdgeCandidate> candidates = buildComponentCandidates(graph, components);
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.edge.getBaseCost()));

        UnionFind uf = new UnionFind(components.size());
        List<Link> selected = new ArrayList<>();
        double total = 0;

        for (ComponentEdgeCandidate candidate : candidates) {
            if (uf.union(candidate.componentA, candidate.componentB)) {
                selected.add(candidate.edge);
                total += candidate.edge.getBaseCost();
                if (selected.size() == components.size() - 1) {
                    break;
                }
            }
        }

        return new ConnectivityResult(false, components.size(), components, selected, total);
    }

    private List<ComponentEdgeCandidate> buildComponentCandidates(Graph graph, List<List<Integer>> components) {
        List<ComponentEdgeCandidate> result = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            for (int j = i + 1; j < components.size(); j++) {
                Link best = minBridgeLink(graph, components.get(i), components.get(j));
                result.add(new ComponentEdgeCandidate(i, j, best));
            }
        }
        return result;
    }

    private Link minBridgeLink(Graph graph, List<Integer> componentA, List<Integer> componentB) {
        Link best = null;
        for (int aId : componentA) {
            for (int bId : componentB) {
                Node a = graph.node(aId).orElseThrow(() -> new IllegalStateException("Unknown node: " + aId));
                Node b = graph.node(bId).orElseThrow(() -> new IllegalStateException("Unknown node: " + bId));
                double dist = GeoUtils.haversine(a.getLat(), a.getLon(), b.getLat(), b.getLon());
                if (best == null || dist < best.getBaseCost()) {
                    int fromId = Math.min(aId, bId);
                    int toId = Math.max(aId, bId);
                    best = new Link(fromId, toId, dist, LinkType.FIBER, 100);
                }
            }
        }
        return best;
    }

    private record ComponentEdgeCandidate(int componentA, int componentB, Link edge) {
    }

    private static final class UnionFind {
        private final int[] parent;
        private final int[] rank;

        private UnionFind(int n) {
            this.parent = new int[n];
            this.rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        private int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        private boolean union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) {
                return false;
            }
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
            return true;
        }
    }
}
