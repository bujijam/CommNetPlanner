package org.example.service;

import org.example.model.Edge;

import java.util.List;

public record SteinerResult(
        int terminalCount,
        int baselineMstLength,
        List<Edge> baselineEdges,
        int improvedLength,
        List<Edge> improvedEdges,
        int improvement,
        boolean usedAuxiliaryPoint,
        double auxiliaryX,
        double auxiliaryY
) {
}
