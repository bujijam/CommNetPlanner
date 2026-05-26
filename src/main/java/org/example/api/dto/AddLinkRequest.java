package org.example.api.dto;

import org.example.model.LinkType;

public record AddLinkRequest(int fromId, int toId, LinkType linkType, int bandwidth) {
}
