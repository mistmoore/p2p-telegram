package dev.p2p.infrastructure.qbittorrent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import dev.p2p.infrastructure.http.HttpGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QbitClientTest {
    private WireMockServer server;
    private WireMockServer jackett;
    private QbitClient client;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        jackett = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        jackett.start();
        var configuration = new ApplicationConfiguration("token", 42L, jackett.baseUrl(), "key", server.baseUrl(), "admin", "password");
        client = new QbitClient(new HttpGateway(), configuration, new ObjectMapper());
    }

    @AfterEach
    void stopServer() {
        server.stop();
        jackett.stop();
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

        assertEquals(true, client.addMagnet("magnet:?xt=urn:btih:abc"));

        server.verify(postRequestedFor(urlEqualTo("/api/v2/auth/login")));
        server.verify(postRequestedFor(urlEqualTo("/api/v2/torrents/add")));
    }

    @Test
    void acceptsNoContentLoginFromRecentQbittorrentVersions() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withStatus(204).withHeader("Set-Cookie", "SID=session; HttpOnly")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add"))
            .withHeader("Cookie", equalTo("SID=session"))
            .willReturn(aResponse().withBody("Ok.")));

        client.addMagnet("magnet:?xt=urn:btih:abc");

        server.verify(postRequestedFor(urlEqualTo("/api/v2/torrents/add")));
    }

    @Test
    void rejectsQbittorrentFailureResponse() {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withHeader("Set-Cookie", "SID=session").withBody("Ok.")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add")).willReturn(aResponse().withBody("Fails.")));

        assertThrows(IllegalStateException.class, () -> client.addMagnet("magnet:?xt=urn:btih:abc"));
    }

    @Test
    void acceptsJsonResultFromRecentQbittorrentVersions() throws Exception {
        jackett.stubFor(get(urlEqualTo("/dl/dontorrent/file.torrent"))
            .willReturn(aResponse().withBody("d3:foo3:bare")));
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withStatus(204).withHeader("Set-Cookie", "SID=session")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add"))
            .withHeader("Cookie", equalTo("SID=session"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                {"added_torrent_ids":["abc"],"failure_count":0,"pending_count":0,"success_count":1}
                """)));

        assertEquals(true, client.addTorrentFile(jackett.baseUrl() + "/dl/dontorrent/file.torrent"));

        server.verify(postRequestedFor(urlEqualTo("/api/v2/torrents/add")));
    }

    @Test
    void rejectsFailedJsonResultFromQbittorrent() {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withStatus(204).withHeader("Set-Cookie", "SID=session")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                {"failure_count":1,"success_count":0}
                """)));

        assertThrows(IllegalStateException.class, () -> client.addMagnet("magnet:?xt=urn:btih:abc"));
    }

    @Test
    void treatsDuplicateMagnetAsAlreadyPresent() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withStatus(204).withHeader("Set-Cookie", "SID=session")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add"))
            .willReturn(aResponse().withStatus(409).withBody("Conflict")));

        assertEquals(false, client.addMagnet("magnet:?xt=urn:btih:abc"));
    }

    @Test
    void treatsDuplicateTorrentFileAsAlreadyPresent() throws Exception {
        jackett.stubFor(get(urlEqualTo("/dl/dontorrent/file.torrent"))
            .willReturn(aResponse().withBody("d3:foo3:bare")));
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withStatus(204).withHeader("Set-Cookie", "SID=session")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add"))
            .willReturn(aResponse().withStatus(409).withBody("Conflict")));

        assertEquals(false, client.addTorrentFile(jackett.baseUrl() + "/dl/dontorrent/file.torrent"));

        jackett.verify(getRequestedFor(urlEqualTo("/dl/dontorrent/file.torrent")));
    }

    @Test
    void downloadsJackettTorrentAndUploadsItToQbittorrent() throws Exception {
        jackett.stubFor(get(urlEqualTo("/dl/dontorrent/file.torrent"))
            .willReturn(aResponse().withHeader("Content-Type", "application/x-bittorrent").withBody("d3:foo3:bare")));
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withHeader("Set-Cookie", "SID=session").withBody("Ok.")));
        server.stubFor(post(urlEqualTo("/api/v2/torrents/add"))
            .withHeader("Cookie", equalTo("SID=session"))
            .withRequestBody(containing("d3:foo3:bare"))
            .willReturn(aResponse().withBody("Ok.")));

        client.addTorrentFile(jackett.baseUrl() + "/dl/dontorrent/file.torrent");

        jackett.verify(getRequestedFor(urlEqualTo("/dl/dontorrent/file.torrent")));
        server.verify(postRequestedFor(urlEqualTo("/api/v2/torrents/add")));
    }

    @Test
    void rejectsTorrentFilesOutsideJackett() {
        assertThrows(IllegalArgumentException.class, () -> client.addTorrentFile("https://example.com/file.torrent"));
    }

    @Test
    void readsDownloadStatusWithSessionCookie() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withHeader("Set-Cookie", "SID=session").withBody("Ok.")));
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/v2/torrents/info"))
            .withHeader("Cookie", equalTo("SID=session"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                [{"name":"ubuntu.iso","progress":0.5,"state":"downloading","dlspeed":2048,"eta":3600}]
                """)));

        var downloads = client.listDownloads();

        assertEquals(1, downloads.size());
        assertEquals("ubuntu.iso", downloads.getFirst().name());
        assertEquals(0.5, downloads.getFirst().progress());
        assertEquals(2048L, downloads.getFirst().downloadSpeed());
    }

    @Test
    void detectsExistingInfoHash() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v2/auth/login"))
            .willReturn(aResponse().withHeader("Set-Cookie", "SID=session").withBody("Ok.")));
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/v2/torrents/info?hashes=0123"))
            .withHeader("Cookie", equalTo("SID=session"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[{\"hash\":\"0123\"}]")));

        assertEquals(true, client.hasInfoHash("0123"));
    }
}
