package com.axenix.blueprints.service;

import com.axenix.blueprints.model.CompilationError;
import com.axenix.blueprints.model.QualityGateResponse;
import com.axenix.blueprints.model.TestResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class QualityGateService {

    public QualityGateResponse check(String code, String testClassName) {
        long started = System.currentTimeMillis();
        Path directory = null;
        Process process = null;

        try {
            if (code == null || code.isBlank()) {
                return error(started, "invalid_input", "Пустой исходный код");
            }


            if (!"TestMonolithTest".equals(testClassName)) {
                return error(
                        started,
                        "invalid_input",
                        "Этот сценарий поддерживает только TestMonolithTest"
                );
            }

            Path testFile = Path.of(
                    "target", "test-classes", "TestMonolithTest.class"
            ).toAbsolutePath();

            if (!Files.isRegularFile(testFile)) {
                return error(
                        started,
                        "infrastructure_error",
                        "Нет скомпилированного теста: " + testFile
                                + ". Выполните mvn test"
                );
            }

            CompilationUnit unit = StaticJavaParser.parse(code);

            boolean correctClass = unit.getTypes().stream().anyMatch(type ->
                    type instanceof ClassOrInterfaceDeclaration
                            && type.getNameAsString().equals("TestMonolith")
                            && type.isPublic()
            );

            if (unit.getPackageDeclaration().isPresent() || !correctClass) {
                return error(started, "invalid_input",
                        "Нужен полный public class TestMonolith без package");
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

            if (compiler == null) {
                return error(started, "infrastructure_error",
                        "Компилятор недоступен. Запустите приложение на JDK");
            }

            directory = Files.createTempDirectory("blueprints-quality-");
            Path source = directory.resolve("TestMonolith.java");
            Path reportFile = directory.resolve("report.properties");
            Path processLog = directory.resolve("process.log");

            Files.writeString(source, code, StandardCharsets.UTF_8);

            String classpath = buildClasspath();
            DiagnosticCollector<JavaFileObject> diagnostics =
                    new DiagnosticCollector<>();

            boolean compiled;

            try (StandardJavaFileManager manager =
                         compiler.getStandardFileManager(
                                 diagnostics, Locale.ROOT,
                                 StandardCharsets.UTF_8
                         )) {

                Iterable<? extends JavaFileObject> files =
                        manager.getJavaFileObjects(source.toFile());

                List<String> options = List.of(
                        "-encoding", "UTF-8",
                        "-proc:none",
                        "-classpath", classpath,
                        "-d", directory.toString()
                );

                compiled = Boolean.TRUE.equals(
                        compiler.getTask(
                                null, manager, diagnostics,
                                options, null, files
                        ).call()
                );
            }

            if (!compiled) {
                List<CompilationError> errors = new ArrayList<>();

                for (Diagnostic<? extends JavaFileObject> diagnostic
                        : diagnostics.getDiagnostics()) {

                    if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                        errors.add(new CompilationError(
                                String.valueOf(diagnostic.getLineNumber()),
                                String.valueOf(diagnostic.getColumnNumber()),
                                diagnostic.getMessage(Locale.ROOT),
                                diagnostic.getKind().name()
                        ));
                    }
                }

                return new QualityGateResponse(
                        false, "compilation_error", 0, 0,
                        elapsed(started), errors, List.of(),
                        "Кандидат не прошёл компиляцию"
                );
            }

            String executable = System.getProperty("os.name")
                    .toLowerCase(Locale.ROOT).contains("win")
                    ? "java.exe" : "java";

            Path java = Path.of(
                    System.getProperty("java.home"), "bin", executable
            );

            // Кандидат ПЕРВЫМ: тесты должны обращаться к новой версии.
            String childClasspath =
                    directory.toAbsolutePath() + File.pathSeparator + classpath;

            ProcessBuilder builder = new ProcessBuilder(
                    java.toString(),
                    "-Xmx256m",
                    "-cp", childClasspath,
                    QualityGateRunner.class.getName(),
                    testClassName,
                    directory.toAbsolutePath().toString(),
                    reportFile.toAbsolutePath().toString()
            );

            builder.redirectErrorStream(true);
            builder.redirectOutput(processLog.toFile());

            builder.environment().keySet().removeIf(
                    key -> key.equalsIgnoreCase("GROQ_API_KEY")
            );

            process = builder.start();

            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);

                return error(started, "timeout",
                        "Превышен лимит выполнения тестов: 15 секунд");
            }

            if (!Files.isRegularFile(reportFile)) {
                return error(started, "infrastructure_error",
                        "Runner не создал отчёт. Код выхода: "
                                + process.exitValue()
                                + ". " + readLog(processLog));
            }

            Properties report = new Properties();

            try (InputStream input = Files.newInputStream(reportFile)) {
                report.load(input);
            }

            int total = Integer.parseInt(report.getProperty("total"));
            int failed = Integer.parseInt(report.getProperty("failed"));
            int ignored = Integer.parseInt(report.getProperty("ignored"));

            List<TestResult> results = new ArrayList<>();

            for (int i = 0; i < failed; i++) {
                results.add(new TestResult(
                        report.getProperty("failure." + i + ".name", "test"),
                        false,
                        "Согласно исходному контракту",
                        "Проверка не пройдена",
                        report.getProperty("failure." + i + ".trace", "")
                ));
            }

            boolean success = process.exitValue() == 0
                    && total > 0 && failed == 0 && ignored == 0;

            return new QualityGateResponse(
                    success,
                    success ? "ok" : "tests_failed",
                    total,
                    Math.max(0, total - failed),
                    elapsed(started),
                    List.of(),
                    results,
                    success
                            ? "Кандидат прошёл " + total + " проверок"
                            : "Проверка не пройдена: выполнено " + total
                            + ", ошибок " + failed
                            + ", пропущено " + ignored
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(started, "infrastructure_error",
                    "Проверка прервана");

        } catch (Exception e) {
            return error(started, "infrastructure_error",
                    e.getClass().getSimpleName() + ": " + e.getMessage());

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (directory != null) {
                deleteDirectory(directory);
            }
        }
    }

    private String buildClasspath() {
        Set<String> entries = new LinkedHashSet<>();

        entries.add(Path.of("target/classes").toAbsolutePath().toString());
        entries.add(Path.of("target/test-classes").toAbsolutePath().toString());

        String current = System.getProperty("java.class.path", "");

        for (String entry : current.split(
                java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry).toAbsolutePath().toString());
            }
        }

        return String.join(File.pathSeparator, entries);
    }

    private String readLog(Path file) {
        try (java.io.Reader reader =
                     Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2000];
            int count = reader.read(buffer);
            return count < 0 ? "" : new String(buffer, 0, count);
        } catch (Exception e) {
            return "Не удалось прочитать журнал runner";
        }
    }

    private QualityGateResponse error(
            long started, String status, String message) {

        return new QualityGateResponse(
                false, status, 0, 0, elapsed(started),
                List.of(), List.of(), message
        );
    }

    private long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }

    private void deleteDirectory(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}