package com.axenix.blueprints.model;

import lombok.Getter;

@Getter
public class Node {
    private String id;
    private String type;      // class, method, loop_for, if, variable, return...
    private String text;
    private int line;
    private String complexity; // O(1), O(n), O(n^2)

    public Node(String id, String type, String text, int line, String complexity) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.line = line;
        this.complexity = complexity;
    }
}
