package org.example.service;

public record ResilienceScore(
        double score,
        int connectedKeyNodes,
        int totalKeyNodes,
        double avgLinkReliability
) {
}
