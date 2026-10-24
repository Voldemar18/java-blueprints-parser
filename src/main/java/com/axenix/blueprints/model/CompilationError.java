package com.axenix.blueprints.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompilationError {
    private String line;
    private String column;
    private String message;
    private String kind;
}
