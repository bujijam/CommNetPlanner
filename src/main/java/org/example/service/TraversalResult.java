package org.example.service;

import java.util.List;

public record TraversalResult(
        boolean returnToSource,
        boolean exactUsed,
        int totalDistance,
        List<Integer> terminalVisitOrder,
        List<Integer> expandedRoute
) {
}
