package dev.aerune1.staffm;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private final Path dataDirectory;
    private final Logger logger;
    private Toml config;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        load();
    }

    public void load() {
        File folder = dataDirectory.toFile();
        if (!folder.exists()) folder.mkdirs();

        File file = new File(folder, "config.toml");
        if (!file.exists()) {
            try (InputStream in = getClass().getResourceAsStream("/default-config.toml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                } else {
                    // Fallback if resource not found
                    file.createNewFile();
                }
            } catch (IOException e) {
                logger.error("Failed to create default config", e);
            }
        }
        config = new Toml().read(file);
    }

    public String getString(String path) {
        return config.getString(path, "Missing: " + path);
    }

    public long getLong(String path) {
        return config.getLong(path, 0L);
    }
}