package org.example.gnn;

import ai.onnxruntime.*;
import org.example.graph.Graph;
import org.example.model.DisasterScenario;
import org.example.model.DisasterType;
import org.example.model.Link;
import org.example.model.LinkType;
import org.example.model.Node;
import org.example.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class GnnInferenceService {

    private static final Logger log = LoggerFactory.getLogger(GnnInferenceService.class);

    private final GnnFeatureExtractor featureExtractor = new GnnFeatureExtractor();
    private OrtEnvironment ortEnv;
    private OrtSession nodeDamageSession;
    private OrtSession linkResistanceSession;
    private boolean onnxAvailable = false;

    public GnnInferenceService() {
        try {
            ortEnv = OrtEnvironment.getEnvironment();
            nodeDamageSession = loadModel("/gnn/node_damage.onnx");
            linkResistanceSession = loadModel("/gnn/link_resistance.onnx");
            onnxAvailable = nodeDamageSession != null && linkResistanceSession != null;
        } catch (Exception e) {
            log.warn("ONNX models not available, using physics fallback: {}", e.getMessage());
        }
    }

    private OrtSession loadModel(String resourcePath) {
        try (InputStream is = GnnInferenceService.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Model file not found: {}", resourcePath);
                return null;
            }
            byte[] modelBytes = is.readAllBytes();
            return ortEnv.createSession(modelBytes);
        } catch (Exception e) {
            log.warn("Failed to load ONNX model {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    public void predict(Graph graph, DisasterScenario scenario) {
        if (onnxAvailable) {
            try {
                predictWithOnnx(graph, scenario);
                return;
            } catch (Exception e) {
                log.warn("ONNX inference failed, falling back to physics model: {}", e.getMessage());
            }
        }
        predictWithPhysics(graph, scenario);
    }

    private void predictWithOnnx(Graph graph, DisasterScenario scenario) throws OrtException {
        float[][] nodeFeatures = featureExtractor.extractNodeFeatures(graph, scenario);
        float[][] linkFeatures = featureExtractor.extractLinkFeatures(graph);

        List<Node> nodes = new ArrayList<>(graph.nodes());

        try (OnnxTensor nodeFeatureTensor = OnnxTensor.createTensor(ortEnv, nodeFeatures);
             OrtSession.Result nodeResult = nodeDamageSession.run(
                     java.util.Map.of("node_features", nodeFeatureTensor))) {
            float[][] nodePredictions = (float[][]) nodeResult.get(0).getValue();
            for (int i = 0; i < nodes.size() && i < nodePredictions.length; i++) {
                nodes.get(i).setPredictedDamageScore(sigmoid(nodePredictions[i][0]));
            }
        }

        List<Link> links = new ArrayList<>(graph.links());
        if (!links.isEmpty()) {
            try (OnnxTensor linkFeatureTensor = OnnxTensor.createTensor(ortEnv, linkFeatures);
                 OrtSession.Result linkResult = linkResistanceSession.run(
                         java.util.Map.of("link_features", linkFeatureTensor))) {
                float[][] linkPredictions = (float[][]) linkResult.get(0).getValue();
                for (int i = 0; i < links.size() && i < linkPredictions.length; i++) {
                    links.get(i).setPredictedResistance(sigmoid(linkPredictions[i][0]));
                }
            }
        }
    }

    public void predictWithPhysics(Graph graph, DisasterScenario scenario) {
        List<Node> nodes = new ArrayList<>(graph.nodes());

        for (Node node : nodes) {
            double distKm = GeoUtils.haversine(
                    node.getLat(), node.getLon(),
                    scenario.getEpicenterLat(), scenario.getEpicenterLon());
            double radius = Math.max(1.0, scenario.getAffectedRadiusKm());
            double nodeTypeBias = nodeTypeBias(node, scenario.getDisasterType());
            double raw = scenario.getMagnitude() - (distKm / radius) * 10.0 + nodeTypeBias;
            node.setPredictedDamageScore(sigmoid(raw));
        }

        for (Link link : graph.links()) {
            Node nodeA = graph.node(link.getFromId()).orElse(null);
            Node nodeB = graph.node(link.getToId()).orElse(null);
            double damageA = nodeA != null ? nodeA.getPredictedDamageScore() : 0.5;
            double damageB = nodeB != null ? nodeB.getPredictedDamageScore() : 0.5;
            double linkTypeResilience = linkTypeResilience(link.getLinkType());
            double resistance = 1.0 - (1 - damageA) * (1 - damageB) * linkTypeResilience;
            link.setPredictedResistance(Math.max(0.0, Math.min(1.0, resistance)));
        }
    }

    private double nodeTypeBias(Node node, DisasterType disasterType) {
        if (node.getNodeType() == null) return 0;
        return switch (node.getNodeType()) {
            case COMMAND  -> -1.0;
            case HOSPITAL -> -0.5;
            case SHELTER  -> 0.0;
            case RELAY    -> 0.5;
            case AFFECTED -> 1.5;
        };
    }

    private double linkTypeResilience(LinkType linkType) {
        if (linkType == null) return 0.7;
        return switch (linkType) {
            case FIBER     -> 0.6;
            case MICROWAVE -> 0.8;
            case SATELLITE -> 0.95;
        };
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
