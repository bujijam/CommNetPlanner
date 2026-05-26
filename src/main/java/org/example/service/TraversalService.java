package org.example.service;

import org.example.graph.Graph;
import org.example.model.Link;
import org.example.model.Node;

import java.util.*;

public class TraversalService {
    private static final int INF = Integer.MAX_VALUE / 4;
    private static final int EXACT_LIMIT = 12;

    public TraversalResult solve(Graph graph, int sourceId, boolean returnToSource) {
        if (graph.node(sourceId).isEmpty()) {
            throw new IllegalArgumentException("Source node not found: " + sourceId);
        }
        List<Integer> ids = graph.nodes().stream().map(Node::getId).sorted().toList();
        if (ids.size() < 2) {
            throw new IllegalArgumentException("At least two nodes required.");
        }

        Map<Integer, DijkstraSnapshot> snapshots = new HashMap<>();
        for (int id : ids) {
            snapshots.put(id, dijkstra(graph, id));
        }
        ensureReachable(ids, snapshots);

        List<Integer> terminalOrder;
        boolean exactUsed;
        if (ids.size() - 1 <= EXACT_LIMIT) {
            terminalOrder = exactOrder(ids, sourceId, returnToSource, snapshots);
            exactUsed = true;
        } else {
            terminalOrder = heuristicOrder(ids, sourceId, returnToSource, snapshots);
            exactUsed = false;
        }

        int total = routeDistance(terminalOrder, snapshots);
        List<Integer> expanded = expandRoute(terminalOrder, snapshots);
        return new TraversalResult(returnToSource, exactUsed, total, terminalOrder, expanded);
    }

    private void ensureReachable(List<Integer> ids, Map<Integer, DijkstraSnapshot> snapshots) {
        for (int from : ids) {
            for (int to : ids) {
                if (from == to) { continue; }
                if (snapshots.get(from).dist().getOrDefault(to, INF) >= INF) {
                    throw new IllegalArgumentException("Graph is not connected, cannot complete full traversal.");
                }
            }
        }
    }

    private List<Integer> exactOrder(
            List<Integer> ids,
            int sourceId,
            boolean returnToSource,
            Map<Integer, DijkstraSnapshot> snapshots
    ) {
        List<Integer> nodes = ids.stream().filter(id -> id != sourceId).toList();
        int m = nodes.size();
        int maxMask = 1 << m;

        int[][] dp = new int[maxMask][m];
        int[][] parent = new int[maxMask][m];
        for (int mask = 0; mask < maxMask; mask++) {
            Arrays.fill(dp[mask], INF);
            Arrays.fill(parent[mask], -1);
        }

        for (int j = 0; j < m; j++) {
            int to = nodes.get(j);
            dp[1 << j][j] = distance(sourceId, to, snapshots);
        }

        for (int mask = 0; mask < maxMask; mask++) {
            for (int end = 0; end < m; end++) {
                if ((mask & (1 << end)) == 0 || dp[mask][end] >= INF) {
                    continue;
                }
                for (int next = 0; next < m; next++) {
                    if ((mask & (1 << next)) != 0) {
                        continue;
                    }
                    int nextMask = mask | (1 << next);
                    int candidate = dp[mask][end] + distance(nodes.get(end), nodes.get(next), snapshots);
                    if (candidate < dp[nextMask][next]) {
                        dp[nextMask][next] = candidate;
                        parent[nextMask][next] = end;
                    }
                }
            }
        }

        int fullMask = maxMask - 1;
        int bestEnd = -1;
        int bestCost = INF;
        for (int end = 0; end < m; end++) {
            int candidate = dp[fullMask][end];
            if (returnToSource) {
                candidate += distance(nodes.get(end), sourceId, snapshots);
            }
            if (candidate < bestCost) {
                bestCost = candidate;
                bestEnd = end;
            }
        }

        List<Integer> orderReversed = new ArrayList<>();
        int mask = fullMask;
        int cursor = bestEnd;
        while (cursor != -1) {
            orderReversed.add(nodes.get(cursor));
            int prev = parent[mask][cursor];
            mask ^= (1 << cursor);
            cursor = prev;
        }

        List<Integer> order = new ArrayList<>();
        order.add(sourceId);
        for (int i = orderReversed.size() - 1; i >= 0; i--) {
            order.add(orderReversed.get(i));
        }
        if (returnToSource) {
            order.add(sourceId);
        }
        return order;
    }

    private List<Integer> heuristicOrder(
            List<Integer> ids,
            int sourceId,
            boolean returnToSource,
            Map<Integer, DijkstraSnapshot> snapshots
    ) {
        Set<Integer> unvisited = new HashSet<>(ids);
        unvisited.remove(sourceId);
        List<Integer> route = new ArrayList<>();
        route.add(sourceId);
        int current = sourceId;
        while (!unvisited.isEmpty()) {
            int currentNode = current;
            int next = unvisited.stream()
                    .min(Comparator.comparingInt(nodeId -> distance(currentNode, nodeId, snapshots)))
                    .orElseThrow();
            route.add(next);
            unvisited.remove(next);
            current = next;
        }
        if (returnToSource) {
            route.add(sourceId);
        }
        twoOptImprove(route, returnToSource, snapshots);
        return route;
    }

    private void twoOptImprove(List<Integer> route, boolean cycle, Map<Integer, DijkstraSnapshot> snapshots) {
        int n = route.size();
        if (n < 4) { return; }
        boolean improved;
        int loops = 0;
        do {
            improved = false;
            loops++;
            for (int i = 1; i < n - 2; i++) {
                for (int k = i + 1; k < n - 1; k++) {
                    if (!cycle && k == n - 1) { continue; }
                    int a = route.get(i - 1);
                    int b = route.get(i);
                    int c = route.get(k);
                    int d = route.get(k + 1);
                    int before = distance(a, b, snapshots) + distance(c, d, snapshots);
                    int after = distance(a, c, snapshots) + distance(b, d, snapshots);
                    if (after < before) {
                        reverseSegment(route, i, k);
                        improved = true;
                    }
                }
            }
        } while (improved && loops < 20);
    }

    private int routeDistance(List<Integer> route, Map<Integer, DijkstraSnapshot> snapshots) {
        int total = 0;
        for (int i = 1; i < route.size(); i++) {
            total += distance(route.get(i - 1), route.get(i), snapshots);
        }
        return total;
    }

    private List<Integer> expandRoute(List<Integer> terminalOrder, Map<Integer, DijkstraSnapshot> snapshots) {
        List<Integer> expanded = new ArrayList<>();
        if (terminalOrder.isEmpty()) { return expanded; }
        expanded.add(terminalOrder.get(0));
        for (int i = 1; i < terminalOrder.size(); i++) {
            int from = terminalOrder.get(i - 1);
            int to = terminalOrder.get(i);
            List<Integer> segment = reconstructPath(from, to, snapshots.get(from).prev());
            for (int j = 1; j < segment.size(); j++) {
                expanded.add(segment.get(j));
            }
        }
        return expanded;
    }

    private List<Integer> reconstructPath(int from, int to, Map<Integer, Integer> prev) {
        List<Integer> reversed = new ArrayList<>();
        Integer cursor = to;
        while (cursor != null) {
            reversed.add(cursor);
            if (cursor == from) { break; }
            cursor = prev.get(cursor);
        }
        List<Integer> path = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    private DijkstraSnapshot dijkstra(Graph graph, int sourceId) {
        Map<Integer, Integer> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();
        for (Node node : graph.nodes()) {
            dist.put(node.getId(), INF);
            prev.put(node.getId(), null);
        }
        dist.put(sourceId, 0);
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingInt(NodeDistance::distance));
        pq.offer(new NodeDistance(sourceId, 0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            if (current.distance > dist.get(current.nodeId)) { continue; }
            for (Map.Entry<Integer, Link> entry : graph.neighbors(current.nodeId).entrySet()) {
                int nextId = entry.getKey();
                int candidate = current.distance + (int) Math.round(entry.getValue().getBaseCost());
                if (candidate < dist.getOrDefault(nextId, INF)) {
                    dist.put(nextId, candidate);
                    prev.put(nextId, current.nodeId);
                    pq.offer(new NodeDistance(nextId, candidate));
                }
            }
        }
        return new DijkstraSnapshot(dist, prev);
    }

    private int distance(int fromId, int toId, Map<Integer, DijkstraSnapshot> snapshots) {
        return snapshots.get(fromId).dist().getOrDefault(toId, INF);
    }

    private void reverseSegment(List<Integer> route, int left, int right) {
        while (left < right) {
            int t = route.get(left);
            route.set(left, route.get(right));
            route.set(right, t);
            left++;
            right--;
        }
    }

    private record DijkstraSnapshot(Map<Integer, Integer> dist, Map<Integer, Integer> prev) {
    }

    private record NodeDistance(int nodeId, int distance) {
    }
}
