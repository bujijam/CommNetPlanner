package org.example.service;

import java.util.List;

public record ShortestPathResult(int sourceId, List<PathEntry> entries) {
    public record PathEntry(int targetId, int distance, boolean reachable, List<Integer> path) {
    }
}
