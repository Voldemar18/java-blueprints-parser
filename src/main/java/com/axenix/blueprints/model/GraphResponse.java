package com.axenix.blueprints.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GraphResponse {
    private List<Node> nodes;
    private List<Edge> edges;
}