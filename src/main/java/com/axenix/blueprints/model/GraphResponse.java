package com.axenix.blueprints.model;

import java.util.List;

public class GraphResponse {
    private List<Node> nodes;
    private List<Edge> edges;

    public GraphResponse(List<Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<Node> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }
}