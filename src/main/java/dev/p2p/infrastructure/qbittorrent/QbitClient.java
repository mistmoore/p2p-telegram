package dev.p2p.infrastructure.qbittorrent;

import dev.p2p.infrastructure.config.ApplicationConfiguration;
import dev.p2p.infrastructure.http.HttpGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

@ApplicationScoped
public class QbitClient implements dev.p2p.application.port.out.TorrentDownloadPort {
    private final HttpGateway http;
    private final ApplicationConfiguration configuration;

    @Inject
    public QbitClient(HttpGateway http, ApplicationConfiguration configuration) {
        this.http = http;
        this.configuration = configuration;
    }

    private String login() throws Exception {
        var response = http.post(configuration.qbitUrl() + "/api/v2/auth/login", Map.of("username", configuration.qbitUser(), "password", configuration.qbitPassword()), null);
        if (!response.body().trim().equals("Ok.")) throw new IllegalStateException("Login qBittorrent rechazado");
        return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    @Override
    public void addMagnet(String magnetLink) throws Exception {
        var response = http.post(configuration.qbitUrl() + "/api/v2/torrents/add", Map.of("urls", magnetLink), login());
        if (!response.body().trim().equalsIgnoreCase("Ok.")) throw new IllegalStateException("qBittorrent rechazó el magnet link");
    }
}
