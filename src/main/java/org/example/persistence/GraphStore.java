package org.example.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.graph.Graph;
import org.example.model.*;

public class GraphStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public GraphStore() {}

    public String exportGeoJson(Graph graph) {
        ObjectNode root = MAPPER.createObjectNode();

        ObjectNode nodeCollection = MAPPER.createObjectNode();
        nodeCollection.put("type", "FeatureCollection");
        ArrayNode nodeFeatures = MAPPER.createArrayNode();
        for (Node node : graph.nodes()) {
            ObjectNode feature = MAPPER.createObjectNode();
            feature.put("type", "Feature");
            ObjectNode geometry = MAPPER.createObjectNode();
            geometry.put("type", "Point");
            ArrayNode coords = MAPPER.createArrayNode();
            coords.add(node.getLon());
            coords.add(node.getLat());
            geometry.set("coordinates", coords);
            feature.set("geometry", geometry);
            ObjectNode props = MAPPER.createObjectNode();
            props.put("id", node.getId());
            props.put("name", node.getName() != null ? node.getName() : "");
            props.put("description", node.getDescription() != null ? node.getDescription() : "");
            props.put("nodeType", node.getNodeType() != null ? node.getNodeType().name() : "RELAY");
            props.put("population", node.getPopulation());
            props.put("operational", node.isOperational());
            props.put("predictedDamageScore", node.getPredictedDamageScore());
            feature.set("properties", props);
            nodeFeatures.add(feature);
        }
        nodeCollection.set("features", nodeFeatures);
        root.set("nodes", nodeCollection);

        ArrayNode linksArray = MAPPER.createArrayNode();
        for (Link link : graph.links()) {
            ObjectNode linkNode = MAPPER.createObjectNode();
            linkNode.put("fromId", link.getFromId());
            linkNode.put("toId", link.getToId());
            linkNode.put("linkType", link.getLinkType() != null ? link.getLinkType().name() : "FIBER");
            linkNode.put("bandwidth", link.getBandwidth());
            linkNode.put("baseCost", link.getBaseCost());
            linkNode.put("predictedResistance", link.getPredictedResistance());
            linksArray.add(linkNode);
        }
        root.set("links", linksArray);

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("GeoJSON export failed", e);
        }
    }

    public Graph importGeoJson(String geoJson) {
        Graph graph = new Graph();
        try {
            JsonNode root = MAPPER.readTree(geoJson);
            JsonNode nodeCollection = root.get("nodes");
            if (nodeCollection != null && nodeCollection.has("features")) {
                for (JsonNode feature : nodeCollection.get("features")) {
                    JsonNode props = feature.get("properties");
                    JsonNode geometry = feature.get("geometry");
                    Node node = new Node();
                    node.setId(props.get("id").asInt());
                    node.setName(props.has("name") ? props.get("name").asText() : "");
                    node.setDescription(props.has("description") ? props.get("description").asText() : "");
                    if (props.has("nodeType")) {
                        try { node.setNodeType(NodeType.valueOf(props.get("nodeType").asText())); }
                        catch (IllegalArgumentException ignored) { node.setNodeType(NodeType.RELAY); }
                    }
                    node.setPopulation(props.has("population") ? props.get("population").asInt() : 0);
                    node.setOperational(props.has("operational") ? props.get("operational").asBoolean(true) : true);
                    node.setPredictedDamageScore(props.has("predictedDamageScore") ? props.get("predictedDamageScore").asDouble() : 0.0);
                    if (geometry != null && geometry.has("coordinates")) {
                        JsonNode coords = geometry.get("coordinates");
                        node.setLon(coords.get(0).asDouble());
                        node.setLat(coords.get(1).asDouble());
                    }
                    graph.upsertNode(node);
                }
            }
            JsonNode linksArray = root.get("links");
            if (linksArray != null && linksArray.isArray()) {
                for (JsonNode linkNode : linksArray) {
                    Link link = new Link();
                    link.setFromId(linkNode.get("fromId").asInt());
                    link.setToId(linkNode.get("toId").asInt());
                    if (linkNode.has("linkType")) {
                        try { link.setLinkType(LinkType.valueOf(linkNode.get("linkType").asText())); }
                        catch (IllegalArgumentException ignored) { link.setLinkType(LinkType.FIBER); }
                    }
                    link.setBandwidth(linkNode.has("bandwidth") ? linkNode.get("bandwidth").asInt() : 100);
                    // Use Haversine distance if baseCost is missing or zero (e.g. imported GeoJSON with default 0)
                    double baseCost = linkNode.has("baseCost") ? linkNode.get("baseCost").asDouble() : 0.0;
                    if (baseCost <= 0.0) {
                        Node fromNode = graph.node(link.getFromId()).orElse(null);
                        Node toNode = graph.node(link.getToId()).orElse(null);
                        if (fromNode != null && toNode != null) {
                            baseCost = org.example.util.GeoUtils.haversine(
                                    fromNode.getLat(), fromNode.getLon(),
                                    toNode.getLat(), toNode.getLon());
                        }
                    }
                    link.setBaseCost(baseCost);
                    link.setPredictedResistance(linkNode.has("predictedResistance") ? linkNode.get("predictedResistance").asDouble() : 0.0);
                    graph.upsertLink(link);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("GeoJSON import failed", e);
        }
        return graph;
    }
}
