package org.example.graph;

import org.example.model.Edge;
import org.example.model.City;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Graph {
    // （城市索引表）{id: 城市}
    private final Map<Integer, City> citiesById = new LinkedHashMap<>();
    // （邻接表）{id: {邻居id: 边}}
    private final Map<Integer, Map<Integer, Edge>> adjacency = new LinkedHashMap<>();
    // （边索引表）{"起点id-终点id": 边}
    private final Map<String, Edge> edgesByKey = new LinkedHashMap<>();

    // 新增/修改城市
    public void upsertCity(City city){
        Objects.requireNonNull(city, "城市不能为空");

        citiesById.put(city.id(), city);
        // 确保该城市（键）的值不为空
        adjacency.computeIfAbsent(city.id(), ignored -> new LinkedHashMap<>());
        // 重算与该城市相连边的长度
        recomputeIncidentEdges(city.id());
    }

    // 新增/修改边
    public Edge upsertEdge(int fromId, int toId){
        City from = requireCity(fromId);
        City to = requireCity(toId);
        int length = calculateRoundedDistance(from, to);
        return putEdgeInternal(fromId, toId, length);
    }

    // 返回一条规范化的边（更新了邻接表和边索引表）
    private Edge putEdgeInternal(int fromId, int toId, int length) {
        ensureDifferentVertices(fromId, toId);
        int a = Math.min(fromId, toId);
        int b = Math.max(fromId, toId);
        Edge normalized = new Edge(a, b, length);
        edgesByKey.put(normalizedEdgeKey(a, b), normalized);
        // 更新邻接表
        adjacency.computeIfAbsent(a, ignored -> new LinkedHashMap<>()).put(b, normalized);
        adjacency.computeIfAbsent(b, ignored -> new LinkedHashMap<>()).put(a, normalized);
        return normalized;
    }

    // 生成边所对应的键，小id在前大id在后
    private String normalizedEdgeKey(int fromId, int toId) {
        int a = Math.min(fromId, toId);
        int b = Math.min(fromId, toId);
        return a + "-" + b;
    }

    // 确保起点与终点不同
    private void ensureDifferentVertices(int fromId, int toId) {
        if (fromId == toId) {
            throw new IllegalArgumentException("不能有回路: " + fromId);
        }
    }

    // 计算两城市距离并四舍五入
    public static int calculateRoundedDistance(City from, City to) {
        // 转换成long，避免平方时溢出
        long dx = (long) from.x() - to.x();
        long dy = (long) from.y() - to.y();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy));
    }

    private City requireCity(int cityId) {
        City city = citiesById.get(cityId);
        if (city == null) {
            throw new IllegalArgumentException("未找到城市：" + cityId);
        }
        return city;
    }

    // 重新计算相连边的长度
    private void recomputeIncidentEdges(int cityId) {
        if (!adjacency.containsKey(cityId)) { return; }

        List<Integer> neighbors = List.copyOf(adjacency.get(cityId).keySet());
        for (int neighborId : neighbors) {
            upsertEdge(cityId, neighborId);
        }
    }


}
