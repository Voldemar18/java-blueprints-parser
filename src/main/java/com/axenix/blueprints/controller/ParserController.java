package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.GraphResponse;
import com.axenix.blueprints.service.JavaParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/parse")
@CrossOrigin(origins = "*") // Чтобы Postman/Frontend могли стучаться
public class ParserController {

    public record EditRequest(String code, String nodeId, String text) {}

    @Autowired
    private JavaParserService parserService;

    @PostMapping
    public ResponseEntity<?> parseJavaFile(@RequestParam("file") MultipartFile file) {
        try {
            String code = new String(file.getBytes(), StandardCharsets.UTF_8);
            GraphResponse response = parserService.parse(code);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Ошибка чтения файла: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Ошибка парсинга кода: " + e.getMessage());
        }
    }

    @PostMapping("/edit")
    public ResponseEntity<?> editNode(@RequestBody EditRequest request) {
        try {
            String updated = parserService.updateNode(request.code(), request.nodeId(), request.text());
            return ResponseEntity.ok(java.util.Map.of("code", updated, "graph", parserService.parse(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }
}
