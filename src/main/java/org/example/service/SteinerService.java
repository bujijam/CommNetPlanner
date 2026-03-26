package org.example.service;

import org.example.graph.Graph;
import org.example.model.Edge;

import java.util.*;

public class SteinerService {
    // 执行 Steiner 近似分析并返回对比结果
    public SteinerResult analyze(Graph graph) {
        List<Point> terminals = graph.cities().stream() // 遍历所有城市
                .map(city -> new Point(city.x(), city.y(), city.id())) // 转成几何点表示（保留城市ID）
                .toList(); // 收集成终端点列表
        if (terminals.size() < 2) { // 若终端点少于2个
            throw new IllegalArgumentException("至少需要两个城市才能进行 Steiner 分析。"); // 直接报错
        }

        MstResult baselineResult = mst(terminals); // 先计算仅终端点的MST作为基线
        int baseline = baselineResult.length(); // 记录基线总长度
        int bestLength = baseline; // 当前最优长度初始化为基线
        Point bestAux = null; // 当前最优辅助点初始化为空
        MstResult bestMst = baselineResult; // 当前最优树初始化为基线树

        List<Point> candidates = buildCandidatePoints(terminals); // 构建辅助点候选集合
        for (Point candidate : candidates) { // 遍历每个候选点进行尝试
            List<Point> withAux = new ArrayList<>(terminals); // 复制终端点列表
            withAux.add(candidate); // 把候选辅助点加入集合
            MstResult result = mst(withAux); // 计算“终端+辅助点”的MST
            if (result.length() < bestLength) { // 若总长度更短
                bestLength = result.length(); // 更新最优长度
                bestAux = candidate; // 记录最优辅助点
                bestMst = result; // 记录最优MST结构
            }
        }
        List<Edge> improvedEdges = toTerminalEdges(bestMst, terminals); // 把最优树映射为终端边用于UI展示

        return new SteinerResult( // 构造并返回分析结果对象
                terminals.size(), // 终端城市数量
                baseline, // 基线MST长度
                toTerminalEdges(baselineResult, terminals), // 基线边集（终端映射后）
                bestLength, // 改进后长度
                improvedEdges, // 改进后边集
                baseline - bestLength, // 改进值（节省长度）
                bestAux != null, // 是否使用了辅助点
                bestAux == null ? Double.NaN : bestAux.x, // 辅助点X坐标（若无则NaN）
                bestAux == null ? Double.NaN : bestAux.y // 辅助点Y坐标（若无则NaN）
        );
    }

    // 构造辅助点候选集合
    private List<Point> buildCandidatePoints(List<Point> terminals) {
        List<Point> candidates = new ArrayList<>(); // 候选点容器
        int n = terminals.size(); // 终端数量
        for (int i = 0; i < n; i++) { // 逐个终端作为中心点
            Point a = terminals.get(i); // 当前中心终端
            List<Point> nearest = terminals.stream() // 在所有终端中搜索近邻
                    .filter(p -> p.id != a.id) // 排除自身
                    .sorted(Comparator.comparingDouble(p -> distance(a, p))) // 按几何距离升序排序
                    .limit(3) // 只取最近的3个点，控制候选规模
                    .toList(); // 收集为列表
            for (int j = 0; j < nearest.size(); j++) { // 近邻两两组合
                for (int k = j + 1; k < nearest.size(); k++) { // 取三元组 (a,b,c)
                    Point b = nearest.get(j); // 组合点b
                    Point c = nearest.get(k); // 组合点c
                    candidates.add(fermatLikePoint(a, b, c)); // 生成费马点近似并加入候选
                }
            }
        }
        return candidates; // 返回全部候选点
    }

    private Point fermatLikePoint(Point a, Point b, Point c) {
        double x = (a.x + b.x + c.x) / 3.0;
        double y = (a.y + b.y + c.y) / 3.0;
        for (int iter = 0; iter < 40; iter++) {
            double wa = 1.0 / Math.max(1e-6, Math.hypot(x - a.x, y - a.y));
            double wb = 1.0 / Math.max(1e-6, Math.hypot(x - b.x, y - b.y));
            double wc = 1.0 / Math.max(1e-6, Math.hypot(x - c.x, y - c.y));
            double sum = wa + wb + wc;
            x = (wa * a.x + wb * b.x + wc * c.x) / sum;
            y = (wa * a.y + wb * b.y + wc * c.y) / sum;
        }
        return new Point(x, y, -1);
    }

    private MstResult mst(List<Point> points) {
        int n = points.size();
        boolean[] used = new boolean[n];
        double[] minDist = new double[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            minDist[i] = Double.POSITIVE_INFINITY;
            parent[i] = -1;
        }
        minDist[0] = 0;
        int total = 0;
        List<TreeEdge> edges = new ArrayList<>();

        for (int step = 0; step < n; step++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!used[i] && (u == -1 || minDist[i] < minDist[u])) {
                    u = i;
                }
            }
            used[u] = true;
            total += (int) Math.round(minDist[u]);
            if (parent[u] >= 0) {
                Point a = points.get(parent[u]);
                Point b = points.get(u);
                edges.add(new TreeEdge(a, b, (int) Math.round(minDist[u])));
            }
            for (int v = 0; v < n; v++) {
                if (used[v]) {
                    continue;
                }
                double d = distance(points.get(u), points.get(v));
                if (d < minDist[v]) {
                    minDist[v] = d;
                    parent[v] = u;
                }
            }
        }
        return new MstResult(total, List.copyOf(edges));
    }

    private double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private List<Edge> toTerminalEdges(MstResult mstResult, List<Point> terminals) {
        Set<Edge> result = new LinkedHashSet<>();
        List<Point> viaAux = new ArrayList<>();
        for (TreeEdge edge : mstResult.edges()) {
            boolean aTerminal = edge.a.id > 0;
            boolean bTerminal = edge.b.id > 0;
            if (aTerminal && bTerminal) {
                int fromId = Math.min(edge.a.id, edge.b.id);
                int toId = Math.max(edge.a.id, edge.b.id);
                result.add(new Edge(fromId, toId, edge.length));
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

    private List<Edge> mstForTerminals(List<Point> terminals) {
        if (terminals.size() < 2) {
            return List.of();
        }
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
        List<Edge> edges = new ArrayList<>();
        for (int step = 0; step < n; step++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!used[i] && (u == -1 || minDist[i] < minDist[u])) {
                    u = i;
                }
            }
            used[u] = true;
            if (parent[u] >= 0) {
                Point a = points.get(parent[u]);
                Point b = points.get(u);
                int length = (int) Math.round(distance(a, b));
                edges.add(new Edge(Math.min(a.id, b.id), Math.max(a.id, b.id), length));
            }
            for (int v = 0; v < n; v++) {
                if (used[v]) {
                    continue;
                }
                double d = distance(points.get(u), points.get(v));
                if (d < minDist[v]) {
                    minDist[v] = d;
                    parent[v] = u;
                }
            }
        }
        return edges;
    }

    private static final class Point {
        private final double x;
        private final double y;
        private final int id;

        private Point(double x, double y, int id) {
            this.x = x;
            this.y = y;
            this.id = id;
        }
    }

    private record TreeEdge(Point a, Point b, int length) {
    }

    private record MstResult(int length, List<TreeEdge> edges) {
    }
}
