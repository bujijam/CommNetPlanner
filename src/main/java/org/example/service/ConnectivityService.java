package org.example.service;

import org.example.graph.Graph;
import org.example.model.City;

import java.util.*;

public class ConnectivityService {
    public boolean isConnected(Graph graph) {
        return components(graph).size() <= 1;
    }

    // 计算图的所有连通分量
    public List<List<Integer>> components(Graph graph) {
        Set<Integer> visited = new HashSet<>(); // 记录已访问城市，避免重复遍历
        List<List<Integer>> components = new ArrayList<>(); // 存放所有分量结果
        for (City city : graph.cities()) { // 逐个城市尝试作为分量起点
            if (visited.contains(city.id())) { // 若该城市已被某次 DFS 覆盖
                continue; // 直接跳过
            }
            List<Integer> component = new ArrayList<>(); // 创建当前分量列表
            ArrayDeque<Integer> stack = new ArrayDeque<>(); // 使用栈实现迭代版 DFS
            stack.push(city.id()); // 把起点入栈
            visited.add(city.id()); // 标记起点已访问
            while (!stack.isEmpty()) { // 只要栈不空就持续扩展
                int current = stack.pop(); // 取出一个待处理节点
                component.add(current); // 加入当前分量
                for (int neighborId : graph.neighbors(current).keySet()) { // 遍历当前节点邻居
                    if (visited.add(neighborId)) { // 若邻居此前未访问，则标记并返回 true
                        stack.push(neighborId); // 把新邻居压栈，后续继续深搜
                    }
                }
            }
            component.sort(Integer::compareTo); // 对分量内城市ID排序，输出更稳定
            components.add(component); // 收集当前分量
        }
        return components; // 返回全部分量
    }
}
