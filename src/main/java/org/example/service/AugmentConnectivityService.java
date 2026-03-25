package org.example.service;

import org.example.graph.Graph;
import org.example.model.City;
import org.example.model.Edge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AugmentConnectivityService {
    private final ConnectivityService connectivityService = new ConnectivityService();

    // 执行连通性分析并给出补边建议
    public ConnectivityResult analyze(Graph graph) {
        List<List<Integer>> components = connectivityService.components(graph); // 先计算连通分量
        if (components.size() <= 1) { // 若只有0或1个分量
            return new ConnectivityResult(true, components.size(), components, List.of(), 0); // 直接返回“已连通，无需补边”
        }

        List<ComponentEdgeCandidate> candidates = buildComponentCandidates(graph, components); // 构造所有分量对的候选最短桥边
        candidates.sort(Comparator.comparingInt(candidate -> candidate.edge.length())); // 按候选边长度升序排序，准备贪心选边

        UnionFind uf = new UnionFind(components.size()); // 初始化并查集，每个分量先是独立集合
        List<Edge> selected = new ArrayList<>(); // 存放最终选中的建议补边
        int total = 0; // 统计建议边总长度

        for (ComponentEdgeCandidate candidate : candidates) { // 从短边到长边扫描候选
            if (uf.union(candidate.componentA, candidate.componentB)) { // 若该边连接了两个原本不连通的分量
                selected.add(candidate.edge); // 选入建议结果
                total += candidate.edge.length(); // 累计总长度
                if (selected.size() == components.size() - 1) { // 若已达到连通所需的最小边数
                    break; // 提前结束循环
                }
            }
        }

        return new ConnectivityResult(false, components.size(), components, selected, total); // 返回分析结果（原图不连通）
    }

    private List<ComponentEdgeCandidate> buildComponentCandidates(Graph graph, List<List<Integer>> components) {
        List<ComponentEdgeCandidate> result = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            for (int j = i + 1; j < components.size(); j++) {
                Edge best = minBridgeEdge(graph, components.get(i), components.get(j));
                result.add(new ComponentEdgeCandidate(i, j, best));
            }
        }
        return result;
    }

    private Edge minBridgeEdge(Graph graph, List<Integer> componentA, List<Integer> componentB) {
        Edge best = null;
        for (int aId : componentA) {
            for (int bId : componentB) {
                City a = graph.city(aId).orElseThrow(() -> new IllegalStateException("Unknown city: " + aId));
                City b = graph.city(bId).orElseThrow(() -> new IllegalStateException("Unknown city: " + bId));
                int length = Graph.calculateRoundedDistance(a, b);
                if (best == null || length < best.length()) {
                    int fromId = Math.min(aId, bId);
                    int toId = Math.max(aId, bId);
                    best = new Edge(fromId, toId, length);
                }
            }
        }
        return best;
    }

    private record ComponentEdgeCandidate(int componentA, int componentB, Edge edge) {
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

        // 尝试合并两个集合，成功返回 true
        private boolean union(int a, int b) {
            int rootA = find(a); // 查找 a 的根节点
            int rootB = find(b); // 查找 b 的根节点
            if (rootA == rootB) { // 若根相同，说明已在同一集合
                return false; // 合并失败（会成环）
            }
            if (rank[rootA] < rank[rootB]) { // 若 A 树更矮
                parent[rootA] = rootB; // 把 A 挂到 B 下
            } else if (rank[rootA] > rank[rootB]) { // 若 B 树更矮
                parent[rootB] = rootA; // 把 B 挂到 A 下
            } else { // 若两棵树同高
                parent[rootB] = rootA; // 任意选一个作为新根
                rank[rootA]++; // 新根高度+1
            }
            return true; // 返回合并成功
        }
    }
}
