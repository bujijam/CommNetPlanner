package org.example.gnn;

import org.example.graph.Graph;
import org.example.model.DisasterScenario;
import org.example.model.Link;
import org.example.model.LinkType;
import org.example.model.Node;
import org.example.model.NodeType;
import org.example.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

public class GnnFeatureExtractor {

    private static final double MAX_POPULATION = 1_000_000.0;
    private static final double MAX_BANDWIDTH = 10_000.0;
    private static final double MAX_BASE_COST = 1_000.0;

    public float[][] extractNodeFeatures(Graph graph, DisasterScenario scenario) {
        List<Node> nodes = new ArrayList<>(graph.nodes());
        float[][] features = new float[nodes.size()][9];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            float[] typeEnc = encodeNodeType(node.getNodeType());
            System.arraycopy(typeEnc, 0, features[i], 0, 5);
            features[i][5] = (float) Math.min(1.0, node.getPopulation() / MAX_POPULATION);
            features[i][6] = (float) ((node.getLat() - 20.0) / 30.0);
            features[i][7] = (float) ((node.getLon() - 100.0) / 50.0);
            double distToEpicenter = GeoUtils.haversine(
                    node.getLat(), node.getLon(),
                    scenario.getEpicenterLat(), scenario.getEpicenterLon());
            features[i][8] = (float) Math.min(1.0, distToEpicenter / Math.max(1.0, scenario.getAffectedRadiusKm() * 2));
        }
        return features;
    }

    public long[][] extractEdgeIndex(Graph graph) {
        List<Link> links = new ArrayList<>(graph.links());
        List<Node> nodes = new ArrayList<>(graph.nodes());
        java.util.Map<Integer, Integer> idxMap = new java.util.HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            idxMap.put(nodes.get(i).getId(), i);
        }
        long[][] edgeIndex = new long[2][links.size() * 2];
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            int fromIdx = idxMap.getOrDefault(link.getFromId(), 0);
            int toIdx = idxMap.getOrDefault(link.getToId(), 0);
            edgeIndex[0][i * 2] = fromIdx;
            edgeIndex[1][i * 2] = toIdx;
            edgeIndex[0][i * 2 + 1] = toIdx;
            edgeIndex[1][i * 2 + 1] = fromIdx;
        }
        return edgeIndex;
    }

    public float[][] extractLinkFeatures(Graph graph) {
        List<Link> links = new ArrayList<>(graph.links());
        float[][] features = new float[links.size()][5];
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            float[] typeEnc = encodeLinkType(link.getLinkType());
            System.arraycopy(typeEnc, 0, features[i], 0, 3);
            features[i][3] = (float) Math.min(1.0, link.getBandwidth() / MAX_BANDWIDTH);
            features[i][4] = (float) Math.min(1.0, link.getBaseCost() / MAX_BASE_COST);
        }
        return features;
    }

    public float[] buildDisasterFeature(DisasterScenario scenario) {
        float[] feat = new float[4];
        switch (scenario.getDisasterType()) {
            case EARTHQUAKE -> { feat[0] = 0; feat[1] = 0; }
            case FLOOD      -> { feat[0] = 0; feat[1] = 1; }
            case WILDFIRE   -> { feat[0] = 1; feat[1] = 0; }
            case TYPHOON    -> { feat[0] = 1; feat[1] = 1; }
        }
        feat[2] = (float) (scenario.getMagnitude() / 10.0);
        feat[3] = (float) Math.min(1.0, scenario.getAffectedRadiusKm() / 500.0);
        return feat;
    }

    private float[] encodeNodeType(NodeType type) {
        float[] enc = new float[5];
        if (type == null) { return enc; }
        switch (type) {
            case COMMAND  -> enc[0] = 1;
            case HOSPITAL -> enc[1] = 1;
            case SHELTER  -> enc[2] = 1;
            case RELAY    -> enc[3] = 1;
            case AFFECTED -> enc[4] = 1;
        }
        return enc;
    }

    private float[] encodeLinkType(LinkType type) {
        float[] enc = new float[3];
        if (type == null) { return enc; }
        switch (type) {
            case FIBER     -> enc[0] = 1;
            case MICROWAVE -> enc[1] = 1;
            case SATELLITE -> enc[2] = 1;
        }
        return enc;
    }
}
