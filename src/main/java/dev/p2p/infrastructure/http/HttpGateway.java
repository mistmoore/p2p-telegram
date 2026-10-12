package dev.p2p.infrastructure.http;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class HttpGateway {
    private final HttpClient client;
    private final Duration requestTimeout;
    private final int getRetryAttempts;

    @Inject
    public HttpGateway(
        @ConfigProperty(name = "app.http.connect-timeout-seconds", defaultValue = "5") long connectTimeoutSeconds,
        @ConfigProperty(name = "app.http.request-timeout-seconds", defaultValue = "12") long requestTimeoutSeconds,
        @ConfigProperty(name = "app.http.get-retry-attempts", defaultValue = "3") int getRetryAttempts) {
        if (connectTimeoutSeconds <= 0 || requestTimeoutSeconds <= 0 || getRetryAttempts <= 0) throw new IllegalArgumentException("Los timeouts HTTP y los reintentos deben ser positivos");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
        requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.getRetryAttempts = getRetryAttempts;
    }

    public HttpGateway() { this(5, 12, 3); }

    public static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
    }

    public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    public HttpResponse<String> get(String url) throws Exception { return get(url, requestTimeout); }

    public HttpResponse<String> get(String url, Duration timeout) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        return sendWithRetry(request);
    }

    public HttpResponse<String> get(String url, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12));
        if (cookie != null) request.header("Cookie", cookie);
        return sendWithRetry(request.GET().build());
    }

    public HttpResponse<String> post(String url, Map<String, String> values, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout).header("Content-Type", "application/x-www-form-urlencoded");
        if (cookie != null) request.header("Cookie", cookie);
        return send(request.POST(HttpRequest.BodyPublishers.ofString(form(values))).build());
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return send(request);
            } catch (HttpGatewayException exception) {
                if (!exception.isRetryable() || attempt == getRetryAttempts) throw exception;
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new HttpGatewayException("Reintento HTTP interrumpido", interrupted);
                }
            }
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new HttpGatewayException(response.statusCode());
            return response;
        } catch (java.io.IOException exception) {
            throw new HttpGatewayException("Error de transporte HTTP", exception);
        }
    }
}
