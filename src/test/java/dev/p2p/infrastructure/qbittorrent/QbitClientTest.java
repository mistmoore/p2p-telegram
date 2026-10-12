package dev.p2p.infrastructure.qbittorrent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import dev.p2p.infrastructure.http.HttpGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QbitClientTest {
    private WireMockServer server;
    private QbitClient client;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        var configuration = new ApplicationConfiguration("token", 42L, "http://localhost:9117", "key", server.baseUrl(), "admin", "password");
        client = new QbitClient(new HttpGateway(), configuration);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void logsInAndAddsMagnetWithSessionCookie() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .withFormParam("username", equalTo("admin"))
            .withFormParam("password", equalTo("password"))
            .willReturn(aResponse().withHeader("Set-Cookie", "SID=session; HttpOnly").withBody("Ok.")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add"))
            .withHeader("Cookie", equalTo("SID=session"))
            .withFormParam("urls", equalTo("magnet:?xt=urn:btih:abc"))
            .willReturn(aResponse().withBody("Ok.")));

        client.addMagnet("magnet:?xt=urn:btih:abc");

        server.verify(postRequestedFor(urlEqualTo("/api/v2/auth/login")));
        server.verify(postRequestedFor(urlEqualTo("/api/v2/torrents/add")));
    }

    @Test
    void rejectsQbittorrentFailureResponse() {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withHeader("Set-Cookie", "SID=session").withBody("Ok.")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add")).willReturn(aResponse().withBody("Fails.")));

        assertThrows(IllegalStateException.class, () -> client.addMagnet("magnet:?xt=urn:btih:abc"));
    }
}
