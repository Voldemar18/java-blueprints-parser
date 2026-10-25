package com.axenix.blueprints.controller;

import com.axenix.blueprints.service.AiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class AiAnalysisController {
    private final AiAnalysisService service;

    public AiAnalysisController(AiAnalysisService service) {
        this.service = service;
    }

    public record AnalysisRequest(String code) {}

    @PostMapping
    public ResponseEntity<?> analyze(@RequestBody AnalysisRequest request) {
        try {
            return ResponseEntity.ok(service.analyze(request == null ? null : request.code()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(
                    Map.of(
                            "status", "error",
                            "message", e.getReason() == null
                                    ? "Ошибка анализа"
                                    : e.getReason()
                    )
            );
        }
    }
}