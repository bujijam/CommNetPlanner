package org.example.api;

import org.example.api.dto.DisasterSteinerRequest;
import org.example.api.dto.ShortestPathRequest;
import org.example.gnn.GnnInferenceService;
import org.example.graph.Graph;
import org.example.model.DisasterScenario;
import org.example.model.Link;
import org.example.model.Node;
import org.example.service.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final Graph graph;
    private final ShortestPathService shortestPathService;
    private final ConnectivityService connectivityService;
    private final AugmentConnectivityService augmentConnectivityService;
    private final SteinerService steinerService;
    private final DisasterSteinerService disasterSteinerService;
    private final GnnInferenceService gnnInferenceService;
    private final NetworkResilienceService networkResilienceService;

    public AnalysisController(Graph graph,
                              ShortestPathService shortestPathService,
                              ConnectivityService connectivityService,
                              AugmentConnectivityService augmentConnectivityService,
                              @Qualifier("steinerService") SteinerService steinerService,
                              DisasterSteinerService disasterSteinerService,
                              GnnInferenceService gnnInferenceService,
                              NetworkResilienceService networkResilienceService) {
        this.graph = graph;
        this.shortestPathService = shortestPathService;
        this.connectivityService = connectivityService;
        this.augmentConnectivityService = augmentConnectivityService;
        this.steinerService = steinerService;
        this.disasterSteinerService = disasterSteinerService;
        this.gnnInferenceService = gnnInferenceService;
        this.networkResilienceService = networkResilienceService;
    }

    @PostMapping("/shortest-path")
    public ResponseEntity<ShortestPathResult> shortestPath(@RequestBody ShortestPathRequest req) {
        return ResponseEntity.ok(shortestPathService.calculate(graph, req.sourceId()));
    }

    @PostMapping("/connectivity")
    public ResponseEntity<ConnectivityResult> connectivity() {
        return ResponseEntity.ok(augmentConnectivityService.analyze(graph));
    }

    @PostMapping("/steiner")
    public ResponseEntity<SteinerResult> steiner() {
        return ResponseEntity.ok(steinerService.analyze(graph));
    }

    @PostMapping("/disaster-steiner")
    public ResponseEntity<Map<String, Object>> disasterSteiner(@RequestBody DisasterSteinerRequest req) {
        SteinerResult steinerResult = disasterSteinerService.analyze(graph, req.terminalIds());
        Map<String, Object> response = new HashMap<>();
        List<Node> affectedNodes = new ArrayList<>(graph.nodes());
        List<Link> affectedLinks = new ArrayList<>(graph.links());
        Map<String, Object> gnnResult = new HashMap<>();
        gnnResult.put("nodes", affectedNodes);
        gnnResult.put("links", affectedLinks);
        response.put("gnnResult", gnnResult);
        response.put("steinerResult", steinerResult);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/gnn-predict")
    public ResponseEntity<Map<String, Object>> gnnPredict(@RequestBody DisasterScenario scenario) {
        gnnInferenceService.predict(graph, scenario);
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", new ArrayList<>(graph.nodes()));
        result.put("links", new ArrayList<>(graph.links()));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/resilience")
    public ResponseEntity<ResilienceScore> resilience() {
        return ResponseEntity.ok(networkResilienceService.calculate(graph));
    }
}
