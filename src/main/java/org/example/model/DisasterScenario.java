package org.example.model;

public class DisasterScenario {
    private String scenarioId;
    private String name;
    private DisasterType disasterType;
    private double epicenterLat;
    private double epicenterLon;
    private double magnitude;
    private double affectedRadiusKm;

    public DisasterScenario() {}

    public DisasterScenario(String scenarioId, String name, DisasterType disasterType,
                            double epicenterLat, double epicenterLon,
                            double magnitude, double affectedRadiusKm) {
        this.scenarioId = scenarioId;
        this.name = name;
        this.disasterType = disasterType;
        this.epicenterLat = epicenterLat;
        this.epicenterLon = epicenterLon;
        this.magnitude = magnitude;
        this.affectedRadiusKm = affectedRadiusKm;
    }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DisasterType getDisasterType() { return disasterType; }
    public void setDisasterType(DisasterType disasterType) { this.disasterType = disasterType; }
    public double getEpicenterLat() { return epicenterLat; }
    public void setEpicenterLat(double epicenterLat) { this.epicenterLat = epicenterLat; }
    public double getEpicenterLon() { return epicenterLon; }
    public void setEpicenterLon(double epicenterLon) { this.epicenterLon = epicenterLon; }
    public double getMagnitude() { return magnitude; }
    public void setMagnitude(double magnitude) { this.magnitude = magnitude; }
    public double getAffectedRadiusKm() { return affectedRadiusKm; }
    public void setAffectedRadiusKm(double affectedRadiusKm) { this.affectedRadiusKm = affectedRadiusKm; }
}
