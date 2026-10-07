package com.saurabh.banking.database;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class DatabaseConfig {

    private static final String CONFIG_FILE = "database.properties";

    private final String url;
    private final String username;
    private final String password;

    private DatabaseConfig(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static DatabaseConfig load() {
        Properties properties = new Properties();

        try (InputStream inputStream =
                     DatabaseConfig.class
                             .getClassLoader()
                             .getResourceAsStream(CONFIG_FILE)) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Database configuration file not found: " + CONFIG_FILE
                );
            }

            properties.load(inputStream);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load database configuration",
                    exception
            );
        }

        String url = getRequiredProperty(properties, "db.url");
        String username = getRequiredProperty(properties, "db.username");
        String password = getRequiredProperty(properties, "db.password");

        return new DatabaseConfig(url, username, password);
    }

    private static String getRequiredProperty(
            Properties properties,
            String key
    ) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing database configuration property: " + key
            );
        }

        return value.trim();
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}