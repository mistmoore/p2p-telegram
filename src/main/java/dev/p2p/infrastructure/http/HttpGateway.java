package dev.p2p.infrastructure.http;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class HttpGateway {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
    }

    public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    public HttpResponse<String> get(String url) throws Exception { return get(url, Duration.ofSeconds(12)); }

    public HttpResponse<String> get(String url, Duration timeout) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build());
    }

    public HttpResponse<String> post(String url, Map<String, String> values, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("Content-Type", "application/x-www-form-urlencoded");
        if (cookie != null) request.header("Cookie", cookie);
        return send(request.POST(HttpRequest.BodyPublishers.ofString(form(values))).build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
        return response;
    }
}
