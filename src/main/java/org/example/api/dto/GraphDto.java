package org.example.api.dto;

import org.example.model.Link;
import org.example.model.Node;

import java.util.List;

public record GraphDto(List<Node> nodes, List<Link> links) {
}
