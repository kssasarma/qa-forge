package com.qaforge.infrastructure.filesystem;

import com.qaforge.domain.exception.QaForgeException;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.port.out.TestFileStorePort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Implements {@link TestFileStorePort} on the local filesystem, per PRD §14.3/§14.4. */
@Component
public class LocalTestFileStore implements TestFileStorePort {

    private static final String PLAYWRIGHT_CONFIG = """
        import { defineConfig, devices } from '@playwright/test';

        export default defineConfig({
          testDir: './playwright',
          timeout: 30_000,
          retries: 1,
          use: {
            baseURL: process.env.PLAYWRIGHT_BASE_URL,
            trace: 'retain-on-failure',
          },
          projects: [
            { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
          ],
        });
        """;

    private final String outputBaseDirectory;

    public LocalTestFileStore(@Value("${qaforge.generation.output-base-directory:/tmp/qa-forge-tests}") String outputBaseDirectory) {
        this.outputBaseDirectory = outputBaseDirectory;
    }

    @Override
    public void write(GeneratedTest test, String outputDirectory) {
        Path target = resolvePath(test, outputDirectory);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, test.content(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new QaForgeException("Failed to write generated test file: " + target, e);
        }
    }

    @Override
    public void writeAll(List<GeneratedTest> tests, String outputDirectory) {
        tests.forEach(test -> write(test, outputDirectory));
    }

    @Override
    public void writePlaywrightConfig(String outputDirectory) {
        Path configPath = Paths.get(outputDirectory, "playwright.config.ts");
        if (Files.exists(configPath)) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, PLAYWRIGHT_CONFIG, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new QaForgeException("Failed to write playwright.config.ts: " + configPath, e);
        }
    }

    @Override
    public byte[] exportZip(String repository) {
        Path baseDir = Paths.get(outputBaseDirectory, slugify(repository));
        if (!Files.isDirectory(baseDir)) {
            return new byte[0];
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            try (var paths = Files.walk(baseDir)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String entryName = baseDir.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new QaForgeException("Failed to export test suite ZIP for " + repository, e);
        }
        return buffer.toByteArray();
    }

    private Path resolvePath(GeneratedTest test, String outputDirectory) {
        return switch (test.layer()) {
            case PLAYWRIGHT -> Paths.get(outputDirectory, "playwright", playwrightSubdir(test), test.fileName());
            case REST_ASSURED -> Paths.get(outputDirectory, "rest-assured", "src", "test", "java",
                "com", "qaforge", "generated", test.fileName());
            case DB_VALIDATION -> Paths.get(outputDirectory, "db-validation", "src", "test", "java",
                "com", "qaforge", "generated", test.fileName());
        };
    }

    private String playwrightSubdir(GeneratedTest test) {
        if (test.tags() == null || test.tags().isEmpty()) {
            return "misc";
        }
        return slugify(test.tags().get(0));
    }

    private String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "misc" : slug;
    }
}
