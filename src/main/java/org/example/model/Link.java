package org.example.model;

import java.util.Objects;

public class Link {
    private int fromId;
    private int toId;
    private double baseCost;
    private LinkType linkType;
    private int bandwidth;
    private double predictedResistance;

    public Link() {}

    public Link(int fromId, int toId, double baseCost, LinkType linkType, int bandwidth) {
        this.fromId = fromId;
        this.toId = toId;
        this.baseCost = baseCost;
        this.linkType = linkType;
        this.bandwidth = bandwidth;
        this.predictedResistance = 0.0;
    }

    public int getFromId() { return fromId; }
    public void setFromId(int fromId) { this.fromId = fromId; }
    public int getToId() { return toId; }
    public void setToId(int toId) { this.toId = toId; }
    public double getBaseCost() { return baseCost; }
    public void setBaseCost(double baseCost) { this.baseCost = baseCost; }
    public LinkType getLinkType() { return linkType; }
    public void setLinkType(LinkType linkType) { this.linkType = linkType; }
    public int getBandwidth() { return bandwidth; }
    public void setBandwidth(int bandwidth) { this.bandwidth = bandwidth; }
    public double getPredictedResistance() { return predictedResistance; }
    public void setPredictedResistance(double predictedResistance) { this.predictedResistance = predictedResistance; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Link)) return false;
        Link link = (Link) o;
        return fromId == link.fromId && toId == link.toId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromId, toId);
    }
}
