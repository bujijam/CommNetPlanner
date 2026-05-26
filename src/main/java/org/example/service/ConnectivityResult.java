package org.example.service;

import org.example.model.Link;

import java.util.List;

public record ConnectivityResult(
        boolean connected,
        int componentCount,
        List<List<Integer>> components,
        List<Link> suggestedEdges,
        double suggestedTotalLength
) {
}
