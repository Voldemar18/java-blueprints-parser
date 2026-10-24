package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.QualityGateResponse;
import com.axenix.blueprints.service.QualityGateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/quality")
@CrossOrigin(origins = "*")
public class QualityGateController {

    @Autowired
    private QualityGateService qualityGateService;

    @PostMapping("/check")
    public ResponseEntity<QualityGateResponse> check(@RequestBody String code, @RequestParam(value = "testClass", required = false) String testClassName) {
        try {
            Class<?> testClass = null;
            if (testClassName != null && !testClassName.isEmpty()) {
                testClass = Class.forName(testClassName);
            } else {
                // Try to find test classes in classpath
                // Default to TestMonolith tests if they exist
                try {
                    testClass = Class.forName("TestMonolithTest");
                } catch (Exception e) {
                    try {
                        testClass = Class.forName("com.axenix.TestMonolithTest");
                    } catch (Exception ex) {
                        // Fallback - will fail gracefully
                    }
                }
            }
            if (testClass == null) {
                QualityGateResponse resp = new QualityGateResponse(false, "tests_failed", 0, 0, 0, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList(), "Тестовый класс не найден");
                return ResponseEntity.badRequest().body(resp);
            }
            QualityGateResponse response = qualityGateService.check(code, testClass);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            QualityGateResponse resp = new QualityGateResponse(false, "compilation_error", 0, 0, 0, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/check-file")
    public ResponseEntity<?> checkFile(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                       @RequestParam(value = "testClass", required = false) String testClassName) {
        try {
            String code = new String(file.getBytes(), StandardCharsets.UTF_8);
            Class<?> testClass = null;
            if (testClassName != null && !testClassName.isEmpty()) {
                testClass = Class.forName(testClassName);
            } else {
                try {
                    testClass = Class.forName("TestMonolithTest");
                } catch (Exception e) {
                    try {
                        testClass = Class.forName("com.axenix.TestMonolithTest");
                    } catch (Exception ex) {
                    }
                }
            }
            if (testClass == null) {
                QualityGateResponse resp = new QualityGateResponse(false, "tests_failed", 0, 0, 0, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList(), "Тестовый класс не найден");
                return ResponseEntity.badRequest().body(resp);
            }
            QualityGateResponse response = qualityGateService.check(code, testClass);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            QualityGateResponse resp = new QualityGateResponse(false, "compilation_error", 0, 0, 0, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }
}
