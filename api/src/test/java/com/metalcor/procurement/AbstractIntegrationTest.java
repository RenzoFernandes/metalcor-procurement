package com.metalcor.procurement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Starts one PostgreSQL 17 container for the whole test run, applies ../db/init with Flyway as the owner,
 * loads db/seed/01..04 with psql, and connects the application as metalcor_app (created by V10).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final String OWNER = "metalcor";
    private static final String OWNER_PASSWORD = "owner_test_password";
    private static final String APP_PASSWORD = "app_test_password";
    private static final String READONLY_PASSWORD = "readonly_test_password";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("metalcor") // V10 refers to the database by this name
            .withUsername(OWNER)
            .withPassword(OWNER_PASSWORD);

    static {
        POSTGRES.start();
        Path repoRoot = findRepoRoot();
        migrate(repoRoot.resolve("db").resolve("init"));
        loadSeeds(repoRoot.resolve("db").resolve("seed"));
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "metalcor_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        // Migrations are applied above as the owner, never by the application.
        registry.add("spring.flyway.enabled", () -> "false");
    }

    private static void migrate(Path migrations) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), OWNER, OWNER_PASSWORD)
                .locations("filesystem:" + migrations)
                .placeholders(Map.of(
                        "app_db_password", APP_PASSWORD,
                        "readonly_db_password", READONLY_PASSWORD))
                .load()
                .migrate();
    }

    /** psql, not ScriptUtils: the seed files contain DO blocks. */
    private static void loadSeeds(Path seedDir) {
        List<Path> files;
        try (Stream<Path> stream = Files.list(seedDir)) {
            files = stream
                    .filter(p -> p.getFileName().toString().matches("0[1-4]_.*\\.sql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (files.size() != 4) {
            throw new IllegalStateException("Expected seed files 01 to 04 in " + seedDir + ", found " + files);
        }
        for (Path file : files) {
            String target = "/tmp/" + file.getFileName();
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(file), target);
            try {
                ExecResult result = POSTGRES.execInContainer(
                        "psql", "-v", "ON_ERROR_STOP=1", "-U", OWNER, "-d", "metalcor", "-f", target);
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException("Seed " + file.getFileName() + " failed: " + result.getStderr());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /** Walks up from the working directory (api/ or the repository root) until it finds db/init. */
    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("db").resolve("init"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("db/init not found above " + Path.of("").toAbsolutePath());
    }
}
