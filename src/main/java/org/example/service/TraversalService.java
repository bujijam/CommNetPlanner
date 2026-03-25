package org.example.service;

import org.example.graph.Graph;
import org.example.model.City;
import org.example.model.Edge;

import java.util.*;

public class TraversalService {
    private static final int INF = Integer.MAX_VALUE / 4;
    private static final int EXACT_LIMIT = 12;

    // 求解全城市遍历路径
    public TraversalResult solve(Graph graph, int sourceId, boolean returnToSource) {
        if (graph.city(sourceId).isEmpty()) { // 若源点不存在
            throw new IllegalArgumentException("源城市不存在: " + sourceId); // 抛出明确错误
        }
        List<Integer> ids = graph.cities().stream().map(City::id).sorted().toList(); // 取全部城市ID并排序
        if (ids.size() < 2) { // 至少需要两个城市才有遍历意义
            throw new IllegalArgumentException("至少需要两个城市。");
        }

        Map<Integer, DijkstraSnapshot> snapshots = new HashMap<>(); // 存放每个城市作为源点的最短路快照
        for (int id : ids) { // 遍历每个城市
            snapshots.put(id, dijkstra(graph, id)); // 计算并缓存该源点的最短路结果
        }
        ensureReachable(ids, snapshots); // 校验任意两城市可达，否则无法全遍历

        List<Integer> terminalOrder; // 声明终端访问顺序结果
        boolean exactUsed; // 标记是否使用精确算法
        if (ids.size() - 1 <= EXACT_LIMIT) { // 若规模不大（非源点数量在阈值内）
            terminalOrder = exactOrder(ids, sourceId, returnToSource, snapshots); // 使用位压DP精确求解
            exactUsed = true; // 记录使用精确策略
        } else { // 若规模较大
            terminalOrder = heuristicOrder(ids, sourceId, returnToSource, snapshots); // 使用启发式策略
            exactUsed = false; // 记录使用启发式策略
        }

        int total = routeDistance(terminalOrder, snapshots); // 计算终端序列总距离
        List<Integer> expanded = expandRoute(terminalOrder, snapshots); // 展开为真实逐点路径
        return new TraversalResult(returnToSource, exactUsed, total, terminalOrder, expanded); // 返回结构化结果
    }

    private void ensureReachable(List<Integer> ids, Map<Integer, DijkstraSnapshot> snapshots) {
        for (int from : ids) {
            for (int to : ids) {
                if (from == to) {
                    continue;
                }
                if (snapshots.get(from).dist().getOrDefault(to, INF) >= INF) {
                    throw new IllegalArgumentException("图不连通，无法完成全城市遍历。");
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

        for (int mask = 0; mask < maxMask; mask++) { // 遍历所有访问状态
            for (int end = 0; end < m; end++) { // 枚举当前终点
                if ((mask & (1 << end)) == 0 || dp[mask][end] >= INF) { // 若终点不在状态内或状态不可达
                    continue; // 跳过无效状态
                }
                for (int next = 0; next < m; next++) { // 尝试扩展到下一个未访问点
                    if ((mask & (1 << next)) != 0) { // 若 next 已在状态中
                        continue; // 跳过，避免重复访问
                    }
                    int nextMask = mask | (1 << next); // 构造加入 next 后的新状态
                    int candidate = dp[mask][end] + distance(nodes.get(end), nodes.get(next), snapshots); // 计算候选距离
                    if (candidate < dp[nextMask][next]) { // 若候选更优
                        dp[nextMask][next] = candidate; // 更新最优值
                        parent[nextMask][next] = end; // 记录转移来源，用于后续回溯路径
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
        Set<Integer> unvisited = new HashSet<>(ids); // 初始化未访问集合
        unvisited.remove(sourceId); // 源点已访问，先移除
        List<Integer> route = new ArrayList<>(); // 存放当前构造路径
        route.add(sourceId); // 从源点出发
        int current = sourceId; // 当前所在节点初始化为源点
        while (!unvisited.isEmpty()) { // 直到所有点都访问完
            int currentNode = current; // 固定当前节点，供 lambda 使用
            int next = unvisited.stream() // 在未访问集合中找最近点
                    .min(Comparator.comparingInt(cityId -> distance(currentNode, cityId, snapshots))) // 以最短距离为比较准则
                    .orElseThrow(); // 理论上一定能找到
            route.add(next); // 将最近点加入路径
            unvisited.remove(next); // 标记该点已访问
            current = next; // 移动当前位置
        }
        if (returnToSource) { // 若要求回源
            route.add(sourceId); // 在末尾补回源点形成回路
        }
        twoOptImprove(route, returnToSource, snapshots); // 调用2-opt进行局部优化
        return route;
    }

    private void twoOptImprove(List<Integer> route, boolean cycle, Map<Integer, DijkstraSnapshot> snapshots) {
        int n = route.size();
        if (n < 4) {
            return;
        }
        boolean improved;
        int loops = 0;
        do {
            improved = false;
            loops++;
            for (int i = 1; i < n - 2; i++) {
                for (int k = i + 1; k < n - 1; k++) {
                    if (!cycle && k == n - 1) {
                        continue;
                    }
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
        if (terminalOrder.isEmpty()) {
            return expanded;
        }
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
            if (cursor == from) {
                break;
            }
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
        for (City city : graph.cities()) {
            dist.put(city.id(), INF);
            prev.put(city.id(), null);
        }
        dist.put(sourceId, 0);
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingInt(NodeDistance::distance));
        pq.offer(new NodeDistance(sourceId, 0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            if (current.distance > dist.get(current.cityId)) {
                continue;
            }
            for (Map.Entry<Integer, Edge> entry : graph.neighbors(current.cityId).entrySet()) {
                int nextId = entry.getKey();
                int candidate = current.distance + entry.getValue().length();
                if (candidate < dist.get(nextId)) {
                    dist.put(nextId, candidate);
                    prev.put(nextId, current.cityId);
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

    private record NodeDistance(int cityId, int distance) {
    }
}
