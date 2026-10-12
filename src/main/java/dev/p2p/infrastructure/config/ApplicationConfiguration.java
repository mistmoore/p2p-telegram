package dev.p2p.infrastructure.config;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Startup
@ApplicationScoped
public class ApplicationConfiguration {
    private final String telegramToken;
    private final long telegramAllowedUser;
    private final String jackettUrl;
    private final String jackettApiKey;
    private final String qbitUrl;
    private final String qbitUser;
    private final String qbitPassword;
    private final String stateDatabaseUrl;

    @Inject
    public ApplicationConfiguration(
        @ConfigProperty(name = "app.telegram.token", defaultValue = "") String telegramToken,
        @ConfigProperty(name = "app.telegram.allowed-user", defaultValue = "0") long telegramAllowedUser,
        @ConfigProperty(name = "app.jackett.url", defaultValue = "http://localhost:9117") String jackettUrl,
        @ConfigProperty(name = "app.jackett.key", defaultValue = "") String jackettApiKey,
        @ConfigProperty(name = "app.qbit.url", defaultValue = "http://localhost:8081") String qbitUrl,
        @ConfigProperty(name = "app.qbit.user", defaultValue = "admin") String qbitUser,
        @ConfigProperty(name = "app.qbit.password", defaultValue = "") String qbitPassword,
        @ConfigProperty(name = "app.state.db-url", defaultValue = "jdbc:sqlite:p2p-telegram.db") String stateDatabaseUrl) {
        validate(telegramToken, telegramAllowedUser, jackettUrl, jackettApiKey, qbitUrl, qbitUser, qbitPassword);
        this.telegramToken = telegramToken;
        this.telegramAllowedUser = telegramAllowedUser;
        this.jackettUrl = jackettUrl;
        this.jackettApiKey = jackettApiKey;
        this.qbitUrl = qbitUrl;
        this.qbitUser = qbitUser;
        this.qbitPassword = qbitPassword;
        this.stateDatabaseUrl = stateDatabaseUrl;
    }

    public ApplicationConfiguration(String telegramToken, long telegramAllowedUser, String jackettUrl, String jackettApiKey, String qbitUrl, String qbitUser, String qbitPassword) {
        this(telegramToken, telegramAllowedUser, jackettUrl, jackettApiKey, qbitUrl, qbitUser, qbitPassword, "jdbc:sqlite::memory:");
    }

    static void validate(String token, long allowedUser, String jackettUrl, String jackettKey, String qbitUrl, String qbitUser, String qbitPassword) {
        if (isMissing(token) || allowedUser <= 0 || isMissing(jackettKey) || isMissing(qbitUser) || isMissing(qbitPassword)) {
            throw new IllegalStateException("Faltan TELEGRAM_BOT_TOKEN, TELEGRAM_ALLOWED_USER_ID, JACKETT_API_KEY, QBIT_USER o QBIT_PASSWORD");
        }
        validateHttpUrl("JACKETT_URL", jackettUrl);
        validateHttpUrl("QBIT_URL", qbitUrl);
    }

    private static boolean isMissing(String value) { return value.isBlank() || "__UNSET__".equals(value); }

    private static void validateHttpUrl(String name, String value) {
        var uri = URI.create(value);
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException(name + " debe ser una URL HTTP(S) absoluta");
        }
    }

    public String telegramToken() { return telegramToken; }
    public long telegramAllowedUser() { return telegramAllowedUser; }
    public String jackettUrl() { return jackettUrl; }
    public String jackettApiKey() { return jackettApiKey; }
    public String qbitUrl() { return qbitUrl; }
    public String qbitUser() { return qbitUser; }
    public String qbitPassword() { return qbitPassword; }
    public String stateDatabaseUrl() { return stateDatabaseUrl; }
}
