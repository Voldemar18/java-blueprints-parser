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
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Ожидаются 4 аргумента: "
                            + "testClass, candidateDirectory, "
                            + "reportFile, candidateClass"
            );
        }

        String testClassName = args[0];
        Path candidateDirectory = Path.of(args[1]).toRealPath();
        Path reportFile = Path.of(args[2]);
        String candidateClassName = args[3];

        Class<?> candidate = Class.forName(
                candidateClassName,
                false,
                ClassLoader.getSystemClassLoader()
        );

        Path loadedFrom = Path.of(
                candidate.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()
        ).toRealPath();

        if (!loadedFrom.equals(candidateDirectory)) {
            throw new IllegalStateException(
                    "Загружен не кандидат: "
                            + candidateClassName
                            + " из " + loadedFrom
            );
        }

        Class<?> testClass = Class.forName(testClassName);
        Result result = JUnitCore.runClasses(testClass);

        Properties report = new Properties();

        report.setProperty(
                "total",
                String.valueOf(result.getRunCount())
        );

        report.setProperty(
                "failed",
                String.valueOf(result.getFailureCount())
        );

        report.setProperty(
                "ignored",
                String.valueOf(result.getIgnoreCount())
        );

        int index = 0;

        for (Failure failure : result.getFailures()) {
            report.setProperty(
                    "failure." + index + ".name",
                    failure.getTestHeader()
            );

            report.setProperty(
                    "failure." + index + ".trace",
                    failure.getTrace()
            );

            index++;
        }

        try (OutputStream output = Files.newOutputStream(reportFile)) {
            report.store(output, "Quality Gate report");
        }

        boolean success = result.wasSuccessful()
                && result.getRunCount() > 0
                && result.getIgnoreCount() == 0;

        System.exit(success ? 0 : 1);
    }
}