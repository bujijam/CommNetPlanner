package org.example.graph;

import org.example.model.Edge;
import org.example.model.City;

import java.util.*;

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

    // 从外部（文件）更新边
    public void upsertEdge(Edge edge){
        Objects.requireNonNull(edge, "边不能为空");
        requireCity(edge.fromId());
        requireCity(edge.toId());
        putEdgeInternal(edge.fromId(), edge.toId(), edge.length());
    }

    // 写入内部的邻接表和边索引表，返回一条规范化的边
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

    // 获得边所对应的键，小id在前大id在后
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


    // 删除一个城市及其关联边
    public void removeCity(int cityId){
        if(!citiesById.containsKey(cityId)){
            return;
        }
        // 只读的邻居id列表
        List<Integer> neighbors = List.copyOf(adjacency.getOrDefault(cityId, Map.of()).keySet());
        // 遍历邻居列表
        for (int neighborId: neighbors){
            // 逐条删除关联边
            removeEdge(cityId, neighborId);
        }
        // 删除邻接表和城市索引的记录
        adjacency.remove(cityId);
        citiesById.remove(cityId);
    }

    // 删除无向边
    private void removeEdge(int fromId, int toId) {
        ensureDifferentVertices(fromId, toId);
        int a = Math.min(fromId, toId);
        int b = Math.max(fromId, toId);
        edgesByKey.remove(normalizedEdgeKey(a, b));

        Map<Integer, Edge> neighborOfA = adjacency.get(a);
        if(neighborOfA != null) {
            // 若a的邻居映射存在，则删除a->的邻接关系
            neighborOfA.remove(b);
        }
        Map<Integer, Edge> neighborOfB = adjacency.get(b);
        if(neighborOfB != null){
            neighborOfB.remove(a);
        }
    }

    // 按id查询城市
    public Optional<City> city(int cityId){
        // Optional是一个不可变容器，用于包装一个可能为null的对象，避免空指针异常
        // ofNullable：若对象为null返回一个空的Optional
        return Optional.ofNullable(citiesById.get(cityId));
    }

    // 返回所有城市
    public Collection<City> cities(){
        // 返回只读集合，避免外部直接改变内部状态
        return Collections.unmodifiableCollection(citiesById.values());
    }

    // 判断某城市是否存在
    public boolean hasCity(int cityId){
        return citiesById.containsKey(cityId);
    }

    // 查询某城市的邻居边
    public Map<Integer, Edge> neighbors(int cityId){
        // 返回只读邻接表，若不存在则返回空映射
        return Collections.unmodifiableMap(adjacency.getOrDefault(cityId, Map.of()));
    }

    // 判断边是否存在
    public boolean hasEdge(int fromId, int toId) {
        ensureDifferentVertices(fromId, toId);
        int a = Math.min(fromId, toId);
        int b = Math.max(fromId, toId);
        return edgesByKey.containsKey(normalizedEdgeKey(a, b));
    }

    // 返回所有边
    public Collection<Edge> edges() {
        return Collections.unmodifiableCollection(edgesByKey.values());
    }
}
