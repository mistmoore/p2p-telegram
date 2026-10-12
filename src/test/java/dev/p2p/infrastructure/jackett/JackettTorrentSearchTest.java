package dev.p2p.infrastructure.jackett;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import dev.p2p.infrastructure.http.HttpGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JackettTorrentSearchTest {
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
    void normalizesAndDeduplicatesTorznabResults() throws Exception {
        server.stubFor(get(urlPathEqualTo("/api/v2.0/indexers/all/results/torznab/api"))
            .willReturn(aResponse().withHeader("Content-Type", "application/xml").withBody("""
                <rss><channel>
                  <item><title> Ubuntu\n  ISO </title><link>magnet:?xt=urn:btih:abc</link></item>
                  <item><title>Duplicate</title><link>magnet:?xt=urn:btih:abc</link></item>
                  <item><title>Invalid</title><link>file:///tmp/torrent</link></item>
                  <item><title>HTTP torrent</title><link>https://indexer.example/download/ubuntu.torrent</link></item>
                </channel></rss>""")));

        var configuration = new ApplicationConfiguration("token", 42L, server.baseUrl(), "secret-key", "http://localhost:8081", "admin", "password");
        var results = new JackettTorrentSearch(new HttpGateway(), configuration).search("ubuntu iso");

        assertEquals(2, results.size());
        assertEquals("Ubuntu ISO", results.getFirst().title());
        assertEquals("magnet:?xt=urn:btih:abc", results.getFirst().link());
        assertEquals("HTTP torrent", results.get(1).title());
        assertEquals("https://indexer.example/download/ubuntu.torrent", results.get(1).link());
        server.verify(getRequestedFor(urlPathMatching("/api/v2.0/indexers/all/results/torznab/api")).withQueryParam("q", com.github.tomakehurst.wiremock.client.WireMock.equalTo("ubuntu iso")).withQueryParam("apikey", com.github.tomakehurst.wiremock.client.WireMock.equalTo("secret-key")));
    }

    @Test
    void returnsNoResultsForAnEmptyFeed() throws Exception {
        server.stubFor(get(urlPathEqualTo("/api/v2.0/indexers/all/results/torznab/api"))
            .willReturn(aResponse().withHeader("Content-Type", "application/xml").withBody("<rss><channel/></rss>")));

        var configuration = new ApplicationConfiguration("token", 42L, server.baseUrl(), "key", "http://localhost:8081", "admin", "password");
        var results = new JackettTorrentSearch(new HttpGateway(), configuration).search("ubuntu");

        assertTrue(results.isEmpty());
    }
}
