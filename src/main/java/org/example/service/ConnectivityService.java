package org.example.service;

import org.example.graph.Graph;
import org.example.model.Node;

import java.util.*;

public class ConnectivityService {
    public boolean isConnected(Graph graph) {
        return components(graph).size() <= 1;
    }

    public List<List<Integer>> components(Graph graph) {
        Set<Integer> visited = new HashSet<>();
        List<List<Integer>> components = new ArrayList<>();
        for (Node node : graph.nodes()) {
            if (visited.contains(node.getId())) {
                continue;
            }
            List<Integer> component = new ArrayList<>();
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            stack.push(node.getId());
            visited.add(node.getId());
            while (!stack.isEmpty()) {
                int current = stack.pop();
                component.add(current);
                for (int neighborId : graph.neighbors(current).keySet()) {
                    if (visited.add(neighborId)) {
                        stack.push(neighborId);
                    }
                }
            }
            component.sort(Integer::compareTo);
            components.add(component);
        }
        return components;
    }
}
