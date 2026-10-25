package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.QualityGateResponse;
import com.axenix.blueprints.service.QualityGateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/quality")
@CrossOrigin(origins = "*")
public class QualityGateController {

    private final QualityGateService qualityGateService;

    public QualityGateController(QualityGateService qualityGateService) {
        this.qualityGateService = qualityGateService;
    }

    @PostMapping("/check")
    public ResponseEntity<QualityGateResponse> check(
            @RequestBody String code,
            @RequestParam(
                    value = "testClass",
                    defaultValue = ""
            ) String testClassName
    ) {
        return execute(code, testClassName);
    }

    @PostMapping("/check-file")
    public ResponseEntity<QualityGateResponse> checkFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(
                    value = "testClass",
                    defaultValue = ""
            ) String testClassName
    ) {
        try {
            String code = new String(
                    file.getBytes(), StandardCharsets.UTF_8
            );
            return execute(code, testClassName);

        } catch (IOException e) {
            return ResponseEntity.badRequest().body(
                    new QualityGateResponse(
                            false,
                            "invalid_input",
                            0,
                            0,
                            0,
                            List.of(),
                            List.of(),
                            "Не удалось прочитать файл"
                    )
            );
        }
    }

    private ResponseEntity<QualityGateResponse> execute(
            String code,
            String testClassName
    ) {
        return ResponseEntity.ok(
                qualityGateService.check(code, testClassName)
        );
    }
}