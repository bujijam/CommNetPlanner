package org.example.service;

import org.example.graph.Graph;
import org.example.model.City;
import org.example.model.Edge;

import java.util.*;

public class ShortestPathService {
    private static final int INF = Integer.MAX_VALUE / 4;

    // 计算源点到所有城市的最短路
    public ShortestPathResult calculate(Graph graph, int sourceId) {
        if (graph.city(sourceId).isEmpty()) { // 若源点不存在
            throw new IllegalArgumentException("源城市不存在: " + sourceId); // 抛出明确错误信息
        } // 源点校验结束

        Map<Integer, Integer> dist = new HashMap<>(); // dist 映射：城市ID -> 当前最短距离
        Map<Integer, Integer> prev = new HashMap<>(); // prev 映射：城市ID -> 前驱城市ID
        for (City city : graph.cities()) { // 初始化每个城市状态
            dist.put(city.id(), INF); // 初始设为“无穷大”
            prev.put(city.id(), null); // 前驱未知
        } // 初始化循环结束
        dist.put(sourceId, 0); // 源点到自身距离为0

        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingInt(node -> node.distance)); // 小根堆：按距离最小优先
        pq.offer(new NodeDistance(sourceId, 0)); // 把源点作为第一条候选记录入队

        while (!pq.isEmpty()) { // 只要还有候选节点就继续
            NodeDistance current = pq.poll(); // 取出当前最短候选
            if (current.distance > dist.get(current.cityId)) { // 若该候选已过期（不是当前最优）
                continue; // 跳过过期条目
            } // 过期判断结束
            for (Map.Entry<Integer, Edge> entry : graph.neighbors(current.cityId).entrySet()) { // 遍历当前节点邻边
                int nextId = entry.getKey(); // 邻居城市ID
                int weight = entry.getValue().length(); // 当前边权重（长度）
                int candidate = current.distance + weight; // 计算经由 current 到 next 的候选距离
                if (candidate < dist.get(nextId)) { // 若发现更短路径
                    dist.put(nextId, candidate); // 更新最短距离
                    prev.put(nextId, current.cityId); // 记录前驱，供后续重建路径
                    pq.offer(new NodeDistance(nextId, candidate)); // 把更新后的候选再次入队
                } // 松弛成功分支结束
            } // 邻边遍历结束
        }

        List<ShortestPathResult.PathEntry> entries = new ArrayList<>();
        for (City city : graph.cities()) {
            if (city.id() == sourceId) {
                continue;
            }
            int distance = dist.getOrDefault(city.id(), INF);
            if (distance >= INF) {
                entries.add(new ShortestPathResult.PathEntry(city.id(), -1, false, List.of()));
                continue;
            }
            entries.add(new ShortestPathResult.PathEntry(city.id(), distance, true, reconstructPath(prev, sourceId, city.id())));
        }

        entries.sort(Comparator
                .comparing((ShortestPathResult.PathEntry e) -> !e.reachable())
                .thenComparingInt(e -> e.reachable() ? e.distance() : Integer.MAX_VALUE)
                .thenComparingInt(ShortestPathResult.PathEntry::targetId));

        return new ShortestPathResult(sourceId, entries);
    }

    // 根据前驱映射重建源点到目标点路径
    private List<Integer> reconstructPath(Map<Integer, Integer> prev, int sourceId, int targetId) {
        List<Integer> path = new ArrayList<>(); // 先按“目标回溯到源点”顺序收集
        Integer cursor = targetId; // 从目标点开始回溯
        while (cursor != null) { // 直到前驱为空为止
            path.add(cursor); // 记录当前节点
            if (cursor == sourceId) { // 若已经回到源点
                break; // 停止回溯
            }
            cursor = prev.get(cursor); // 跳到前驱节点继续回溯
        }
        List<Integer> reversed = new ArrayList<>(); // 准备反转得到正向路径
        for (int i = path.size() - 1; i >= 0; i--) { // 从后往前复制
            reversed.add(path.get(i)); // 写入正向序列
        }
        return reversed; // 返回源点->目标点的正向路径
    }

    private record NodeDistance(int cityId, int distance) {
    }
}
