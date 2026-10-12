package dev.p2p.infrastructure.http;

public class HttpGatewayException extends RuntimeException {
    private final Integer statusCode;

    HttpGatewayException(int statusCode) {
        super("HTTP " + statusCode);
        this.statusCode = statusCode;
    }

    HttpGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return statusCode == null || statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }
}
