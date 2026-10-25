package com.axenix.blueprints.service;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class QualityGateRunner {

    public static void main(String[] args) throws Exception {
        Path candidateDirectory = Path.of(args[1]).toRealPath();
        Path reportFile = Path.of(args[2]);

        Class<?> candidate = Class.forName("TestMonolith", false,
                ClassLoader.getSystemClassLoader());

        Path loadedFrom = Path.of(
                candidate.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()
        ).toRealPath();

        if (!loadedFrom.equals(candidateDirectory)) {
            throw new IllegalStateException(
                    "Загружен не кандидат: " + loadedFrom
            );
        }

        Class<?> testClass = Class.forName(args[0]);
        Result result = JUnitCore.runClasses(testClass);

        Properties report = new Properties();
        report.setProperty("total", String.valueOf(result.getRunCount()));
        report.setProperty("failed", String.valueOf(result.getFailureCount()));
        report.setProperty("ignored", String.valueOf(result.getIgnoreCount()));

        int index = 0;
        for (Failure failure : result.getFailures()) {
            report.setProperty("failure." + index + ".name",
                    failure.getTestHeader());
            report.setProperty("failure." + index + ".trace",
                    failure.getTrace());
            index++;
        }

        try (OutputStream out = Files.newOutputStream(reportFile)) {
            report.store(out, "Quality Gate report");
        }

        System.exit(
                result.wasSuccessful() && result.getRunCount() > 0 ? 0 : 1
        );
    }
}