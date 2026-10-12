package dev.p2p.infrastructure.qbittorrent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import dev.p2p.infrastructure.http.HttpGateway;
import dev.p2p.domain.DownloadStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QbitClient implements dev.p2p.application.port.out.TorrentDownloadPort, dev.p2p.application.port.out.DownloadStatusPort, dev.p2p.application.port.out.TorrentPresencePort {
    private final HttpGateway http;
    private final ApplicationConfiguration configuration;
    private final ObjectMapper mapper;

    @Inject
    public QbitClient(HttpGateway http, ApplicationConfiguration configuration, ObjectMapper mapper) {
        this.http = http;
        this.configuration = configuration;
        this.mapper = mapper;
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

    @Override
    public List<DownloadStatus> listDownloads() throws Exception {
        var response = http.get(configuration.qbitUrl() + "/api/v2/torrents/info", login());
        JsonNode payload = mapper.readTree(response.body());
        if (!payload.isArray()) throw new IllegalStateException("Respuesta de qBittorrent no válida");
        var downloads = new ArrayList<DownloadStatus>();
        for (JsonNode torrent : payload) {
            downloads.add(new DownloadStatus(
                torrent.path("name").asText("Sin nombre"),
                torrent.path("progress").asDouble(),
                torrent.path("state").asText("unknown"),
                torrent.path("dlspeed").asLong(),
                torrent.path("eta").asLong(-1)));
        }
        return List.copyOf(downloads);
    }

    @Override
    public boolean hasInfoHash(String infoHash) throws Exception {
        var response = http.get(configuration.qbitUrl() + "/api/v2/torrents/info?hashes=" + HttpGateway.encode(infoHash), login());
        JsonNode payload = mapper.readTree(response.body());
        if (!payload.isArray()) throw new IllegalStateException("Respuesta de qBittorrent no válida");
        return !payload.isEmpty();
    }
}
