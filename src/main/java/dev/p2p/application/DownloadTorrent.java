package dev.p2p.application;

import dev.p2p.application.port.out.TorrentDownloadPort;
import dev.p2p.application.port.out.TelegramUpdateStatePort;
import dev.p2p.application.port.out.TorrentPresencePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class DownloadTorrent {
    private final TorrentDownloadPort torrentDownload;
    private final TelegramUpdateStatePort updateState;
    private final TorrentPresencePort torrentPresence;

    @Inject
    public DownloadTorrent(TorrentDownloadPort torrentDownload, TelegramUpdateStatePort updateState, TorrentPresencePort torrentPresence) {
        this.torrentDownload = torrentDownload;
        this.updateState = updateState;
        this.torrentPresence = torrentPresence;
    }

    public boolean download(long updateId, String link) throws Exception {
        String infoHash = infoHash(link);
        if (infoHash == null && !isHttpUrl(link)) throw new IllegalArgumentException("Solo se admiten magnet links o archivos torrent de Jackett");
        if (!updateState.claimDownload(updateId)) {
            if (infoHash == null || torrentPresence.hasInfoHash(infoHash)) return false;
            updateState.releaseDownloadClaim(updateId);
            if (!updateState.claimDownload(updateId)) throw new IllegalStateException("No se pudo recuperar la descarga pendiente");
        }
        try {
            if (infoHash == null) torrentDownload.addTorrentFile(link);
            else torrentDownload.addMagnet(link);
            return true;
        } catch (Exception exception) {
            updateState.releaseDownloadClaim(updateId);
            throw exception;
        }
    }

    private static String infoHash(String magnetLink) {
        var uri = URI.create(magnetLink);
        if (!"magnet".equalsIgnoreCase(uri.getScheme())) return null;
        String query = uri.getRawSchemeSpecificPart();
        if (query == null || !query.startsWith("?")) return null;
        for (String parameter : query.substring(1).split("&")) {
            String value = URLDecoder.decode(parameter, StandardCharsets.UTF_8);
            if (value.regionMatches(true, 0, "xt=urn:btih:", 0, 12)) return value.substring(12);
        }
        return null;
    }

    private static boolean isHttpUrl(String link) {
        try {
            String scheme = URI.create(link).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
