package dev.p2p.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpGatewayTest {
    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void retriesTransientServerErrors() {
        server.stubFor(get(urlEqualTo("/temporary")).willReturn(aResponse().withStatus(503)));

        var exception = assertThrows(HttpGatewayException.class, () -> new HttpGateway(1, 1, 3).get(server.baseUrl() + "/temporary"));

        assertTrue(exception.isRetryable());
        server.verify(3, getRequestedFor(urlEqualTo("/temporary")));
    }

    @Test
    void doesNotRetryPermanentClientErrors() {
        server.stubFor(get(urlEqualTo("/invalid")).willReturn(aResponse().withStatus(400)));

        var exception = assertThrows(HttpGatewayException.class, () -> new HttpGateway(1, 1, 3).get(server.baseUrl() + "/invalid"));

        assertFalse(exception.isRetryable());
        server.verify(1, getRequestedFor(urlEqualTo("/invalid")));
    }
}
