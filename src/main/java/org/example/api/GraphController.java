package org.example.api;

import org.example.api.dto.AddLinkRequest;
import org.example.api.dto.GraphDto;
import org.example.graph.Graph;
import org.example.model.Link;
import org.example.model.Node;
import org.example.persistence.GraphStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final Graph graph;
    private final GraphStore graphStore;

    public GraphController(Graph graph, GraphStore graphStore) {
        this.graph = graph;
        this.graphStore = graphStore;
    }

    @GetMapping
    public ResponseEntity<GraphDto> getGraph() {
        return ResponseEntity.ok(new GraphDto(
                new ArrayList<>(graph.nodes()),
                new ArrayList<>(graph.links())));
    }

    @PostMapping("/nodes")
    public ResponseEntity<Node> addNode(@RequestBody Node node) {
        graph.upsertNode(node);
        return ResponseEntity.ok(node);
    }

    @PutMapping("/nodes/{id}")
    public ResponseEntity<Node> updateNode(@PathVariable int id, @RequestBody Node node) {
        node.setId(id);
        graph.upsertNode(node);
        return ResponseEntity.ok(node);
    }

    @DeleteMapping("/nodes/{id}")
    public ResponseEntity<Void> deleteNode(@PathVariable int id) {
        graph.removeNode(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/nodes/all")
    public ResponseEntity<Void> clearNodes() {
        graph.clear();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/links")
    public ResponseEntity<Link> addLink(@RequestBody AddLinkRequest req) {
        if (!graph.hasNode(req.fromId()) || !graph.hasNode(req.toId())) {
            return ResponseEntity.badRequest().build();
        }
        Link link = graph.upsertLink(req.fromId(), req.toId());
        link.setLinkType(req.linkType());
        link.setBandwidth(req.bandwidth());
        graph.upsertLink(link);
        return ResponseEntity.ok(link);
    }

    @DeleteMapping("/links/{fromId}/{toId}")
    public ResponseEntity<Void> deleteLink(@PathVariable int fromId, @PathVariable int toId) {
        graph.removeLink(fromId, toId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/links/all")
    public ResponseEntity<Void> clearLinks() {
        graph.clearLinks();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<GraphDto> importGraph(@RequestBody String geoJson) {
        Graph imported = graphStore.importGeoJson(geoJson);
        graph.clear();
        for (Node n : imported.nodes()) { graph.upsertNode(n); }
        for (Link l : imported.links()) { graph.upsertLink(l); }
        return ResponseEntity.ok(new GraphDto(
                new ArrayList<>(graph.nodes()),
                new ArrayList<>(graph.links())));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportGraph() {
        return ResponseEntity.ok(graphStore.exportGeoJson(graph));
    }
}
