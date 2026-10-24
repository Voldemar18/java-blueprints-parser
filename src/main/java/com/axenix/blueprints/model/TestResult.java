package com.axenix.blueprints.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TestResult {
    private String testName;
    private boolean passed;
    private String expected;
    private String actual;
    private String message;
}
