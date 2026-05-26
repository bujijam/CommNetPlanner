package org.example.service;

import org.example.graph.Graph;
import org.example.model.Link;
import org.example.model.LinkType;
import org.example.model.Node;
import org.example.util.GeoUtils;

import java.util.*;

public class SteinerService {

    public SteinerResult analyze(Graph graph) {
        return analyze(graph, false);
    }

    public SteinerResult analyze(Graph graph, boolean operationalOnly) {
        List<Point> terminals = graph.nodes().stream()
                .filter(node -> !operationalOnly || node.isOperational())
                .map(node -> new Point(node.getLat(), node.getLon(), node.getId()))
                .toList();
        if (terminals.size() < 2) {
            throw new IllegalArgumentException("At least two nodes are required for Steiner analysis.");
        }

        MstResult baselineResult = mst(terminals);
        double baseline = baselineResult.length();
        double bestLength = baseline;
        Point bestAux = null;
        MstResult bestMst = baselineResult;

        List<Point> candidates = buildCandidatePoints(terminals);
        for (Point candidate : candidates) {
            List<Point> withAux = new ArrayList<>(terminals);
            withAux.add(candidate);
            MstResult result = mst(withAux);
            if (result.length() < bestLength) {
                bestLength = result.length();
                bestAux = candidate;
                bestMst = result;
            }
        }
        List<Link> improvedEdges = toTerminalLinks(bestMst, terminals);

        return new SteinerResult(
                terminals.size(),
                baseline,
                toTerminalLinks(baselineResult, terminals),
                bestLength,
                improvedEdges,
                baseline - bestLength,
                bestAux != null,
                bestAux == null ? Double.NaN : bestAux.lat,
                bestAux == null ? Double.NaN : bestAux.lon
        );
    }

    protected List<Point> buildCandidatePoints(List<Point> terminals) {
        List<Point> candidates = new ArrayList<>();
        int n = terminals.size();
        for (int i = 0; i < n; i++) {
            Point a = terminals.get(i);
            List<Point> nearest = terminals.stream()
                    .filter(p -> p.id != a.id)
                    .sorted(Comparator.comparingDouble(p -> distance(a, p)))
                    .limit(3)
                    .toList();
            for (int j = 0; j < nearest.size(); j++) {
                for (int k = j + 1; k < nearest.size(); k++) {
                    Point b = nearest.get(j);
                    Point c = nearest.get(k);
                    candidates.add(fermatLikePoint(a, b, c));
                }
            }
        }
        return candidates;
    }

    protected Point fermatLikePoint(Point a, Point b, Point c) {
        double lat = (a.lat + b.lat + c.lat) / 3.0;
        double lon = (a.lon + b.lon + c.lon) / 3.0;
        for (int iter = 0; iter < 40; iter++) {
            double wa = 1.0 / Math.max(1e-6, distance(new Point(lat, lon, -1), a));
            double wb = 1.0 / Math.max(1e-6, distance(new Point(lat, lon, -1), b));
            double wc = 1.0 / Math.max(1e-6, distance(new Point(lat, lon, -1), c));
            double sum = wa + wb + wc;
            lat = (wa * a.lat + wb * b.lat + wc * c.lat) / sum;
            lon = (wa * a.lon + wb * b.lon + wc * c.lon) / sum;
        }
        return new Point(lat, lon, -1);
    }

    protected MstResult mst(List<Point> points) {
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
                if (!used[i] && (u == -1 || minDist[i] < minDist[u])) {
                    u = i;
                }
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
                double d = distance(points.get(u), points.get(v));
                if (d < minDist[v]) {
                    minDist[v] = d;
                    parent[v] = u;
                }
            }
        }
        return new MstResult(total, List.copyOf(edges));
    }

    protected double distance(Point a, Point b) {
        return GeoUtils.haversine(a.lat, a.lon, b.lat, b.lon);
    }

    private List<Link> toTerminalLinks(MstResult mstResult, List<Point> terminals) {
        Set<Link> result = new LinkedHashSet<>();
        List<Point> viaAux = new ArrayList<>();
        Set<Integer> terminalIds = terminals.stream().map(point -> point.id).collect(java.util.stream.Collectors.toSet());
        for (TreeEdge edge : mstResult.edges()) {
            boolean aTerminal = terminalIds.contains(edge.a.id);
            boolean bTerminal = terminalIds.contains(edge.b.id);
            if (aTerminal && bTerminal) {
                int fromId = Math.min(edge.a.id, edge.b.id);
                int toId = Math.max(edge.a.id, edge.b.id);
                result.add(new Link(fromId, toId, edge.length, LinkType.FIBER, 100));
                continue;
            }
            if (aTerminal ^ bTerminal) {
                viaAux.add(aTerminal ? edge.a : edge.b);
            }
        }
        if (viaAux.size() >= 2) {
            result.addAll(mstForTerminals(viaAux));
        }
        if (result.isEmpty() && terminals.size() >= 2) {
            result.addAll(mstForTerminals(terminals));
        }
        return List.copyOf(result);
    }

    private List<Link> mstForTerminals(List<Point> terminals) {
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
                double d = distance(points.get(u), points.get(v));
                if (d < minDist[v]) {
                    minDist[v] = d;
                    parent[v] = u;
                }
            }
        }
        return edges;
    }

    protected static final class Point {
        final double lat;
        final double lon;
        final int id;

        Point(double lat, double lon, int id) {
            this.lat = lat;
            this.lon = lon;
            this.id = id;
        }
    }

    protected record TreeEdge(Point a, Point b, double length) {
    }

    protected record MstResult(double length, List<TreeEdge> edges) {
    }
}
