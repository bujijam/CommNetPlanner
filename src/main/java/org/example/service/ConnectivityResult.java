package org.example.service;

import org.example.model.Edge;

import java.util.List;

public record ConnectivityResult(
        boolean connected,
        int componentCount,
        List<List<Integer>> components,
        List<Edge> suggestedEdges,
        int suggestedTotalLength
) {
}
