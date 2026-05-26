package org.example.model;

public class Node {
    private int id;
    private String name;
    private double lat;
    private double lon;
    private String description;
    private NodeType nodeType;
    private int population;
    private boolean operational;
    private double predictedDamageScore;

    public Node() {}

    public Node(int id, String name, double lat, double lon, String description,
                NodeType nodeType, int population, boolean operational) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.description = description;
        this.nodeType = nodeType;
        this.population = population;
        this.operational = operational;
        this.predictedDamageScore = 0.0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public NodeType getNodeType() { return nodeType; }
    public void setNodeType(NodeType nodeType) { this.nodeType = nodeType; }
    public int getPopulation() { return population; }
    public void setPopulation(int population) { this.population = population; }
    public boolean isOperational() { return operational; }
    public void setOperational(boolean operational) { this.operational = operational; }
    public double getPredictedDamageScore() { return predictedDamageScore; }
    public void setPredictedDamageScore(double predictedDamageScore) { this.predictedDamageScore = predictedDamageScore; }
}
