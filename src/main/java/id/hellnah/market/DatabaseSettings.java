package id.hellnah.market;

import java.util.Locale;
import java.util.Properties;

/** Validated database connection settings. Credentials are never part of the JDBC URL. */
public record DatabaseSettings(String host, int port, String database, String username,
                               String password, String prefix, String sslMode,
                               boolean allowPublicKeyRetrieval, int connectTimeoutMs, int socketTimeoutMs) {
    public DatabaseSettings {
        if (host == null || !host.matches("[A-Za-z0-9_.:\\[\\]-]{1,253}"))
            throw new IllegalArgumentException("Invalid MySQL host");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("MySQL port must be 1..65535");
        if (database == null || !database.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid MySQL database name");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("MySQL username missing");
        if (password == null) throw new IllegalArgumentException("MySQL password missing");
        if (prefix == null || !prefix.matches("[A-Za-z][A-Za-z0-9_]{0,30}"))
            throw new IllegalArgumentException("table-prefix must start with a letter and contain at most 31 letters/digits/underscores");
        sslMode = sslMode == null ? "REQUIRED" : sslMode.toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY").contains(sslMode))
            throw new IllegalArgumentException("Invalid ssl-mode");
        if (connectTimeoutMs < 100 || connectTimeoutMs > 60000 || socketTimeoutMs < 100 || socketTimeoutMs > 60000)
            throw new IllegalArgumentException("MySQL timeouts must be 100..60000 ms");
    }
    public String url() {
        String address = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return "jdbc:mysql://" + address + ":" + port + "/" + database;
    }
    public Properties properties() {
        Properties p = new Properties();
        p.setProperty("user", username); p.setProperty("password", password);
        p.setProperty("sslMode", sslMode);
        p.setProperty("allowPublicKeyRetrieval", Boolean.toString(allowPublicKeyRetrieval));
        p.setProperty("connectTimeout", Integer.toString(connectTimeoutMs));
        p.setProperty("socketTimeout", Integer.toString(socketTimeoutMs));
        p.setProperty("characterEncoding", "UTF-8"); p.setProperty("connectionCollation", "utf8mb4_bin");
        p.setProperty("autoReconnect", "false");
        p.setProperty("allowMultiQueries", "false");
        return p;
    }
    @Override public String toString() {
        return "DatabaseSettings[host=" + host + ", port=" + port + ", database=" + database
                + ", prefix=" + prefix + ", credentials=REDACTED]";
    }
}
