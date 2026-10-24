package com.axenix.blueprints.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Edge {
    private String source;
    private String target;
    private String label;
}