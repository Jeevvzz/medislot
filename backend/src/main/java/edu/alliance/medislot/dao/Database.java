package edu.alliance.medislot.dao;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.flywaydb.core.Flyway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Database configuration and ordered schema migrations for the app. */
public final class Database {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Settings settings;

    private Database() {}

    public static synchronized void init() throws SQLException {
        if (settings == null) settings = settingsFromEnvironment();
        try {
            Flyway.configure()
                    .dataSource(settings.jdbcUrl(), settings.username(), settings.password())
                    .locations(settings.migrations())
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
        } catch (RuntimeException e) {
            throw new SQLException("Unable to initialize the configured database ("
                    + e.getClass().getSimpleName() + "); connection details are withheld.");
        }
    }

    public static Connection getConnection() throws SQLException {
        if (settings == null) init();
        try {
            return DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
        } catch (SQLException e) {
            throw new SQLException("Database connection failed (SQLState " + e.getSQLState()
                    + "); connection details are withheld.");
        }
    }

    private static Settings settingsFromEnvironment() throws SQLException {
        String databaseMode = System.getProperty("medislot.db", System.getenv("MEDISLOT_DB"));
        if ("h2".equalsIgnoreCase(databaseMode)) {
            try {
            String configuredPath = System.getProperty("medislot.h2.path", System.getenv("MEDISLOT_H2_PATH"));
                Path basePath;
                if (configuredPath != null && !configuredPath.isBlank()) {
                    basePath = Path.of(configuredPath).toAbsolutePath().normalize();
                    if (basePath.getFileName() == null) throw new IllegalArgumentException("H2 path must name a database file prefix.");
                    if (basePath.getParent() != null) Files.createDirectories(basePath.getParent());
                } else {
                    Path data = Files.isDirectory(Path.of("backend")) ? Path.of("backend", "data") : Path.of("data");
                    Files.createDirectories(data);
                    basePath = data.resolve("medislot").toAbsolutePath().normalize();
                }
                String base = basePath.toString().replace('\\', '/');
                return new Settings("jdbc:h2:file:" + base
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE",
                        "sa", "", "classpath:db/migration/h2");
            } catch (Exception e) {
                throw new SQLException("Local H2 was explicitly selected but its data directory is unavailable.");
            }
        }
        String dsn = System.getenv("DATABASE_URL");
        if (dsn == null || dsn.isBlank()) {
            throw new SQLException("DATABASE_URL is required. For local development only, explicitly set MEDISLOT_DB=h2.");
        }
        return parseMysqlDsn(dsn.trim());
    }

    /** Parses mysql:// or jdbc:mysql:// without placing the DSN in an error or log. */
    static Settings parseMysqlDsn(String dsn) throws SQLException {
        String uriText = dsn.startsWith("jdbc:") ? dsn.substring("jdbc:".length()) : dsn;
        if (!uriText.startsWith("mysql://")) {
            throw new SQLException("DATABASE_URL must use a mysql:// or jdbc:mysql:// URL; connection details are withheld.");
        }

        try {
            // The managed provider may append a raw JSON TLS option (for example ssl={...}).
            // Remove the whole query before URI parsing, then interpret and sanitize it below.
            int queryStart = uriText.indexOf('?');
            String authorityAndPath = queryStart < 0 ? uriText : uriText.substring(0, queryStart);
            String rawQuery = queryStart < 0 ? "" : uriText.substring(queryStart + 1);
            URI uri = URI.create(authorityAndPath);
            String host = uri.getHost();
            String rawPath = uri.getRawPath();
            if (host == null || rawPath == null || rawPath.length() < 2) {
                throw new IllegalArgumentException("Missing database authority or name.");
            }
            String database = decode(rawPath.substring(1));
            if (database.isBlank() || database.contains("/")) {
                throw new IllegalArgumentException("Invalid database path.");
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 3306;
            String hostPart = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;

            String username = "";
            String password = "";
            String rawUserInfo = uri.getRawUserInfo();
            if (rawUserInfo != null) {
                String[] parts = rawUserInfo.split(":", 2);
                username = decode(parts[0]);
                password = parts.length == 2 ? decode(parts[1]) : "";
            }

            List<String> queryParts = new ArrayList<>();
            boolean tlsSet = false;
            if (!rawQuery.isBlank()) {
                for (String pair : rawQuery.split("&")) {
                    if (pair.isBlank()) continue;
                    int split = pair.indexOf('=');
                    String key = decode(split < 0 ? pair : pair.substring(0, split));
                    String value = decode(split < 0 ? "" : pair.substring(split + 1));
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (lower.equals("user") || lower.equals("username")) {
                        if (username.isBlank()) username = value;
                        continue;
                    }
                    if (lower.equals("password")) {
                        if (password.isBlank()) password = value;
                        continue;
                    }
                    if (lower.equals("ssl")) {
                        key = "sslMode";
                        value = tlsModeFromProviderOption(value);
                        tlsSet = true;
                    } else if (lower.equals("ssl-mode") || lower.equals("sslmode")) {
                        key = "sslMode";
                        value = normalizeTlsMode(value);
                        tlsSet = true;
                    }
                    queryParts.add(encode(key) + "=" + encode(value));
                }
            }
            if (username.isBlank() || password.isBlank()) {
                throw new IllegalArgumentException("Missing database authentication.");
            }
            // Never allow a provider option to silently disable TLS on a hosted connection.
            if (!tlsSet) queryParts.add("sslMode=REQUIRED");
            String jdbcUrl = "jdbc:mysql://" + hostPart + ":" + port + "/" + encodePath(database);
            if (!queryParts.isEmpty()) jdbcUrl += "?" + String.join("&", queryParts);
            return new Settings(jdbcUrl, username, password, "classpath:db/migration/mysql");
        } catch (RuntimeException e) {
            // Do not attach the cause: URI parser exceptions can quote the entire input DSN.
            throw new SQLException("DATABASE_URL could not be parsed as a MySQL DSN; connection details are withheld.");
        }
    }

    private static String tlsModeFromProviderOption(String value) {
        String option = value.trim();
        if (option.startsWith("{")) {
            try {
                JsonNode settings = JSON.readTree(option);
                return settings.path("rejectUnauthorized").asBoolean(false)
                        ? "VERIFY_IDENTITY"
                        : "REQUIRED";
            } catch (Exception ignored) {
                throw new IllegalArgumentException("Invalid TLS option.");
            }
        }
        if (option.equalsIgnoreCase("true") || option.equals("1")) return "VERIFY_IDENTITY";
        // Hosted traffic remains encrypted even if an option asks not to verify the certificate.
        return "REQUIRED";
    }

    private static String normalizeTlsMode(String value) {
        String mode = value.toUpperCase(Locale.ROOT);
        if (mode.equals("DISABLED") || mode.equals("PREFERRED")) return "REQUIRED";
        if (mode.equals("REQUIRED") || mode.equals("VERIFY_CA") || mode.equals("VERIFY_IDENTITY")) return mode;
        throw new IllegalArgumentException("Unsupported TLS mode.");
    }

    private static String decode(String value) {
        // URI user-info/path treats '+' literally; protect it from form-style decoding.
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    record Settings(String jdbcUrl, String username, String password, String migrations) {}
}
