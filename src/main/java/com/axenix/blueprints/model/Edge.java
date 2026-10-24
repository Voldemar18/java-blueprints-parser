package com.axenix.blueprints.model;

public class Edge {
    private String source;
    private String target;
    private String label; // "contains", "next", "true", "false"

    public Edge(String source, String target, String label) {
        this.source = source;
        this.target = target;
        this.label = label;
    }

    public String getSource() { return source; }
    public String getTarget() { return target; }
    public String getLabel() { return label; }
}