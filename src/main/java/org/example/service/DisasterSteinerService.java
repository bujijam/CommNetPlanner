package org.example.service;

import org.example.graph.Graph;
import org.example.model.Link;
import org.example.model.LinkType;
import org.example.model.Node;
import org.example.util.GeoUtils;

import java.util.*;

public class DisasterSteinerService extends SteinerService {

    /** Scales predicted link resistance [0,1] into a path-cost penalty factor. */
    private static final double RESISTANCE_COST_MULTIPLIER = 10.0;

    public SteinerResult analyze(Graph graph, List<Integer> terminalIds) {
        List<Point> terminals = terminalIds.stream()
                .map(id -> graph.node(id).orElseThrow(() -> new IllegalArgumentException("Node not found: " + id)))
                .map(node -> new Point(node.getLat(), node.getLon(), node.getId()))
                .toList();

        if (terminals.size() < 2) {
            throw new IllegalArgumentException("At least two terminal nodes are required for Steiner analysis.");
        }

        MstResult baselineResult = mstDisaster(graph, terminals);
        double baseline = baselineResult.length();
        double bestLength = baseline;
        Point bestAux = null;
        MstResult bestMst = baselineResult;

        List<Point> candidates = buildCandidatePoints(terminals);
        for (Point candidate : candidates) {
            List<Point> withAux = new ArrayList<>(terminals);
            withAux.add(candidate);
            MstResult result = mstDisaster(graph, withAux);
            if (result.length() < bestLength) {
                bestLength = result.length();
                bestAux = candidate;
                bestMst = result;
            }
        }

        List<Link> improvedEdges = toTerminalLinksDisaster(graph, bestMst, terminals);

        return new SteinerResult(
                terminals.size(),
                baseline,
                toTerminalLinksDisaster(graph, baselineResult, terminals),
                bestLength,
                improvedEdges,
                baseline - bestLength,
                bestAux != null,
                bestAux == null ? Double.NaN : bestAux.lat,
                bestAux == null ? Double.NaN : bestAux.lon
        );
    }

    private double disasterDistance(Graph graph, Point a, Point b) {
        double base = GeoUtils.haversine(a.lat, a.lon, b.lat, b.lon);
        double penalty = 1.0;
        if (a.id > 0) {
            Node na = graph.node(a.id).orElse(null);
            if (na != null && na.getPredictedDamageScore() > 0.7) { penalty *= 2.0; }
        }
        if (b.id > 0) {
            Node nb = graph.node(b.id).orElse(null);
            if (nb != null && nb.getPredictedDamageScore() > 0.7) { penalty *= 2.0; }
        }
        if (a.id > 0 && b.id > 0) {
            Link link = graph.links().stream()
                    .filter(l -> (l.getFromId() == Math.min(a.id, b.id) && l.getToId() == Math.max(a.id, b.id)))
                    .findFirst().orElse(null);
            if (link != null) {
                base = link.getBaseCost() * (1 + link.getPredictedResistance() * RESISTANCE_COST_MULTIPLIER);
                return base * penalty;
            }
        }
        return base * penalty;
    }

    private MstResult mstDisaster(Graph graph, List<Point> points) {
        int n = points.size();
        boolean[] used = new boolean[n];
        double[] minDist = new double[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            minDist[i] = Double.POSITIVE_INFINITY;
            parent[i] = -1;
        }
        minDist[0] = 0;
        double total = 0;
        List<TreeEdge> edges = new ArrayList<>();

        for (int step = 0; step < n; step++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!used[i] && (u == -1 || minDist[i] < minDist[u])) { u = i; }
            }
            used[u] = true;
            total += minDist[u];
            if (parent[u] >= 0) {
                Point a = points.get(parent[u]);
                Point b = points.get(u);
                edges.add(new TreeEdge(a, b, minDist[u]));
            }
            for (int v = 0; v < n; v++) {
                if (used[v]) { continue; }
                double d = disasterDistance(graph, points.get(u), points.get(v));
                if (d < minDist[v]) {
                    minDist[v] = d;
                    parent[v] = u;
                }
            }
        }
        return new MstResult(total, List.copyOf(edges));
    }

    private List<Link> toTerminalLinksDisaster(Graph graph, MstResult mstResult, List<Point> terminals) {
        Set<Link> result = new LinkedHashSet<>();
        List<Point> viaAux = new ArrayList<>();
        Set<Integer> terminalIds = terminals.stream().map(p -> p.id).collect(java.util.stream.Collectors.toSet());
        for (TreeEdge edge : mstResult.edges()) {
            boolean aTerminal = terminalIds.contains(edge.a().id);
            boolean bTerminal = terminalIds.contains(edge.b().id);
            if (aTerminal && bTerminal) {
                int fromId = Math.min(edge.a().id, edge.b().id);
                int toId = Math.max(edge.a().id, edge.b().id);
                result.add(new Link(fromId, toId, edge.length(), LinkType.FIBER, 100));
                continue;
            }
            if (aTerminal ^ bTerminal) {
                viaAux.add(aTerminal ? edge.a() : edge.b());
            }
        }
        if (viaAux.size() >= 2) {
            result.addAll(mstForTerminalsFallback(viaAux));
        }
        if (result.isEmpty() && terminals.size() >= 2) {
            result.addAll(mstForTerminalsFallback(terminals));
        }
        return List.copyOf(result);
    }

    private List<Link> mstForTerminalsFallback(List<Point> terminals) {
        if (terminals.size() < 2) { return List.of(); }
        List<Point> points = new ArrayList<>(terminals);
        int n = points.size();
        boolean[] used = new boolean[n];
        double[] minDist = new double[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            minDist[i] = Double.POSITIVE_INFINITY;
            parent[i] = -1;
        }
        minDist[0] = 0;
        List<Link> edges = new ArrayList<>();
        for (int step = 0; step < n; step++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!used[i] && (u == -1 || minDist[i] < minDist[u])) { u = i; }
            }
            used[u] = true;
            if (parent[u] >= 0) {
                Point a = points.get(parent[u]);
                Point b = points.get(u);
                edges.add(new Link(Math.min(a.id, b.id), Math.max(a.id, b.id), minDist[u], LinkType.FIBER, 100));
            }
            for (int v = 0; v < n; v++) {
                if (used[v]) { continue; }
                double d = GeoUtils.haversine(points.get(u).lat, points.get(u).lon,
                        points.get(v).lat, points.get(v).lon);
                if (d < minDist[v]) {
                    minDist[v] = d;
                    parent[v] = u;
                }
            }
        }
        return edges;
    }
}
