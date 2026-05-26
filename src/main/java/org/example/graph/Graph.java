package org.example.graph;

import org.example.model.Link;
import org.example.model.Node;
import org.example.util.GeoUtils;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Graph {
    private final Map<Integer, Node> nodesById = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, Link>> adjacency = new LinkedHashMap<>();
    private final Map<String, Link> linksByKey = new LinkedHashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void upsertNode(Node node) {
        Objects.requireNonNull(node, "node must not be null");
        lock.writeLock().lock();
        try {
            nodesById.put(node.getId(), node);
            adjacency.computeIfAbsent(node.getId(), ignored -> new LinkedHashMap<>());
            recomputeIncidentLinks(node.getId());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Link upsertLink(int fromId, int toId) {
        lock.writeLock().lock();
        try {
            Node from = requireNode(fromId);
            Node to = requireNode(toId);
            double baseCost = GeoUtils.haversine(from.getLat(), from.getLon(), to.getLat(), to.getLon());
            Link existing = getLinkInternal(fromId, toId);
            org.example.model.LinkType linkType = existing != null ? existing.getLinkType() : org.example.model.LinkType.FIBER;
            int bandwidth = existing != null ? existing.getBandwidth() : 100;
            return putLinkInternal(fromId, toId, baseCost, linkType, bandwidth);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void upsertLink(Link link) {
        Objects.requireNonNull(link, "link must not be null");
        lock.writeLock().lock();
        try {
            requireNode(link.getFromId());
            requireNode(link.getToId());
            putLinkInternal(link.getFromId(), link.getToId(), link.getBaseCost(), link.getLinkType(), link.getBandwidth());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Link putLinkInternal(int fromId, int toId, double baseCost,
                                  org.example.model.LinkType linkType, int bandwidth) {
        ensureDifferentVertices(fromId, toId);
        int a = Math.min(fromId, toId);
        int b = Math.max(fromId, toId);
        Link normalized = new Link(a, b, baseCost, linkType, bandwidth);
        Link existing = linksByKey.get(normalizedLinkKey(a, b));
        if (existing != null) {
            normalized.setPredictedResistance(existing.getPredictedResistance());
        }
        linksByKey.put(normalizedLinkKey(a, b), normalized);
        adjacency.computeIfAbsent(a, ignored -> new LinkedHashMap<>()).put(b, normalized);
        adjacency.computeIfAbsent(b, ignored -> new LinkedHashMap<>()).put(a, normalized);
        return normalized;
    }

    private Link getLinkInternal(int fromId, int toId) {
        int a = Math.min(fromId, toId);
        int b = Math.max(fromId, toId);
        return linksByKey.get(normalizedLinkKey(a, b));
    }

    public static String normalizedLinkKey(int fromId, int toId) {
        int a = Math.min(fromId, toId);
        int b = Math.max(fromId, toId);
        return a + "-" + b;
    }

    private void ensureDifferentVertices(int fromId, int toId) {
        if (fromId == toId) {
            throw new IllegalArgumentException("Self-loop not allowed: " + fromId);
        }
    }

    private Node requireNode(int nodeId) {
        Node node = nodesById.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return node;
    }

    private void recomputeIncidentLinks(int nodeId) {
        if (!adjacency.containsKey(nodeId)) { return; }
        List<Integer> neighbors = List.copyOf(adjacency.get(nodeId).keySet());
        for (int neighborId : neighbors) {
            Node from = nodesById.get(nodeId);
            Node to = nodesById.get(neighborId);
            if (from != null && to != null) {
                double baseCost = GeoUtils.haversine(from.getLat(), from.getLon(), to.getLat(), to.getLon());
                Link existing = getLinkInternal(nodeId, neighborId);
                org.example.model.LinkType lt = existing != null ? existing.getLinkType() : org.example.model.LinkType.FIBER;
                int bw = existing != null ? existing.getBandwidth() : 100;
                putLinkInternal(nodeId, neighborId, baseCost, lt, bw);
            }
        }
    }

    public void removeNode(int nodeId) {
        lock.writeLock().lock();
        try {
            if (!nodesById.containsKey(nodeId)) { return; }
            List<Integer> neighbors = List.copyOf(adjacency.getOrDefault(nodeId, Map.of()).keySet());
            for (int neighborId : neighbors) {
                removeLinkInternal(nodeId, neighborId);
            }
            adjacency.remove(nodeId);
            nodesById.remove(nodeId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeLink(int fromId, int toId) {
        lock.writeLock().lock();
        try {
            removeLinkInternal(fromId, toId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void removeLinkInternal(int fromId, int toId) {
        ensureDifferentVertices(fromId, toId);
        int a = Math.min(fromId, toId);
        int b = Math.max(fromId, toId);
        linksByKey.remove(normalizedLinkKey(a, b));
        Map<Integer, Link> neighborOfA = adjacency.get(a);
        if (neighborOfA != null) { neighborOfA.remove(b); }
        Map<Integer, Link> neighborOfB = adjacency.get(b);
        if (neighborOfB != null) { neighborOfB.remove(a); }
    }

    public Optional<Node> node(int nodeId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(nodesById.get(nodeId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<Node> nodes() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(nodesById.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasNode(int nodeId) {
        lock.readLock().lock();
        try {
            return nodesById.containsKey(nodeId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<Integer, Link> neighbors(int nodeId) {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(adjacency.getOrDefault(nodeId, Map.of()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasLink(int fromId, int toId) {
        lock.readLock().lock();
        try {
            ensureDifferentVertices(fromId, toId);
            int a = Math.min(fromId, toId);
            int b = Math.max(fromId, toId);
            return linksByKey.containsKey(normalizedLinkKey(a, b));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<Link> links() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(linksByKey.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            nodesById.clear();
            adjacency.clear();
            linksByKey.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearLinks() {
        lock.writeLock().lock();
        try {
            linksByKey.clear();
            for (Map<Integer, Link> adj : adjacency.values()) {
                adj.clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
