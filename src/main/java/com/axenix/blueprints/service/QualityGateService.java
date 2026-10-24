package com.axenix.blueprints.service;

import com.axenix.blueprints.model.CompilationError;
import com.axenix.blueprints.model.QualityGateResponse;
import com.axenix.blueprints.model.TestResult;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QualityGateService {

    public QualityGateResponse check(String code, Class<?> testClass) {
        long startTime = System.currentTimeMillis();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("javacompile");
            String className = extractPublicClassName(code);
            if (className == null) {
                className = "CompiledClass";
            }
            Path javaFile = tempDir.resolve(className + ".java");
            Files.write(javaFile, code.getBytes(StandardCharsets.UTF_8));

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return new QualityGateResponse(false, "compilation_error", 0, 0, System.currentTimeMillis() - startTime,
                        Collections.emptyList(), Collections.emptyList(), "Java compiler not available");
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(javaFile.toFile()));
            List<String> options = Arrays.asList("-classpath", buildClasspath(), "-d", tempDir.toString());

            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
            boolean success = task.call();
            fileManager.close();

            if (!success) {
                List<CompilationError> errors = diagnostics.getDiagnostics().stream()
                        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .map(d -> new CompilationError(
                                String.valueOf(d.getLineNumber()),
                                String.valueOf(d.getColumnNumber()),
                                d.getMessage(null),
                                d.getKind().toString()
                        ))
                        .collect(Collectors.toList());
                String errorText = errors.stream()
                        .map(e -> "line " + e.getLine() + ":" + e.getColumn() + " " + e.getMessage())
                        .collect(Collectors.joining("\n"));
                return new QualityGateResponse(false, "compilation_error", 0, 0, System.currentTimeMillis() - startTime,
                        errors, Collections.emptyList(), errorText.isEmpty() ? "Ошибка компиляции" : errorText);
            }

            URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, this.getClass().getClassLoader());
            Result result = JUnitCore.runClasses(testClass);
            List<TestResult> testResults = new ArrayList<>();
            for (Failure failure : result.getFailures()) {
                String testName = failure.getTestHeader();
                String trace = failure.getTrace();
                String expected = "N/A";
                String actual = "N/A";
                // Try to extract expected/actual if present
                if (failure.getException() instanceof AssertionError) {
                    String msg = failure.getMessage();
                    if (msg != null) {
                        if (msg.contains("expected:")) {
                            int idx = msg.indexOf("expected:");
                            String sub = msg.substring(idx);
                            if (sub.contains("but was:")) {
                                String[] parts = sub.split("but was:");
                                expected = parts[0].replace("expected:", "").trim();
                                actual = parts.length > 1 ? parts[1].trim() : "N/A";
                            }
                        }
                    }
                }
                testResults.add(new TestResult(testName, false, expected, actual, trace));
            }

            int totalTests = result.getRunCount();
            int passedTests = result.getRunCount() - result.getFailureCount();
            long execTime = System.currentTimeMillis() - startTime;

            if (result.wasSuccessful()) {
                return new QualityGateResponse(true, "ok", totalTests, passedTests, execTime, Collections.emptyList(), testResults, "Все тесты пройдены");
            } else {
                String failedMsg = result.getFailures().isEmpty() ? "Тесты не пройдены" :
                        "Тест " + result.getFailures().get(0).getTestHeader() + " упал, ожидалось " +
                                (result.getFailures().get(0).getMessage() != null ? result.getFailures().get(0).getMessage() : "X") + ", получено Y";
                // Better message
                if (!result.getFailures().isEmpty()) {
                    Failure f = result.getFailures().get(0);
                    String msg = f.getMessage();
                    if (msg != null && msg.contains("expected:") && msg.contains("but was:")) {
                        String[] parts = msg.split("but was:");
                        String exp = parts[0].replace("expected:", "").trim();
                        String act = parts[1].trim().split("\n")[0];
                        failedMsg = "Тест " + f.getTestHeader() + " упал, ожидалось " + exp + ", получено " + act;
                    } else if (msg != null) {
                        failedMsg = "Тест " + f.getTestHeader() + " упал: " + msg;
                    }
                }
                return new QualityGateResponse(false, "tests_failed", totalTests, passedTests, execTime, Collections.emptyList(), testResults, failedMsg);
            }
        } catch (Exception e) {
            long execTime = System.currentTimeMillis() - startTime;
            return new QualityGateResponse(false, "compilation_error", 0, 0, execTime, Collections.emptyList(), Collections.emptyList(), e.getMessage());
        } finally {
            if (tempDir != null) {
                try {
                    deleteDirectory(tempDir.toFile());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String buildClasspath() {
        StringBuilder sb = new StringBuilder();
        String classpath = System.getProperty("java.class.path");
        if (classpath != null && !classpath.isEmpty()) {
            sb.append(classpath);
        }
        // Also add target/classes if exists
        File targetClasses = new File("target/classes");
        if (targetClasses.exists()) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(targetClasses.getAbsolutePath());
        }
        File testClasses = new File("target/test-classes");
        if (testClasses.exists()) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(testClasses.getAbsolutePath());
        }
        return sb.toString();
    }

    private String extractPublicClassName(String code) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)").matcher(code);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern.compile("class\\s+(\\w+)").matcher(code);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
