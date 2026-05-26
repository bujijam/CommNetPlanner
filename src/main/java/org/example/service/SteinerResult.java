package org.example.service;

import org.example.model.Link;

import java.util.List;

public record SteinerResult(
        int terminalCount,
        double baselineMstLength,
        List<Link> baselineEdges,
        double improvedLength,
        List<Link> improvedEdges,
        double improvement,
        boolean usedAuxiliaryPoint,
        double auxiliaryLat,
        double auxiliaryLon
) {
}
