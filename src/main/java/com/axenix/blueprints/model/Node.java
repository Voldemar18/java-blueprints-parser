package com.axenix.blueprints.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Node {
    private String id;
    private String type;      // class, method, loop_for, if, variable, return...
    private String text;
    private int line;
    private String complexity; // O(1), O(n), O(n^2)
}