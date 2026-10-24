package com.axenix.blueprints.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class QualityGateResponse {
    private boolean success;
    private String status; // "ok", "compilation_error", "tests_failed"
    private int totalTests;
    private int passedTests;
    private long executionTimeMs;
    private List<CompilationError> compilationErrors;
    private List<TestResult> testResults;
    private String message;
}
