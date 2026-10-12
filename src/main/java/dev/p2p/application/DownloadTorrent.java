package dev.p2p.application;

import dev.p2p.application.port.out.TorrentDownloadPort;
import dev.p2p.application.port.out.TelegramUpdateStatePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;

@ApplicationScoped
public class DownloadTorrent {
    private final TorrentDownloadPort torrentDownload;
    private final TelegramUpdateStatePort updateState;

    @Inject
    public DownloadTorrent(TorrentDownloadPort torrentDownload, TelegramUpdateStatePort updateState) {
        this.torrentDownload = torrentDownload;
        this.updateState = updateState;
    }

    public boolean download(long updateId, String magnetLink) throws Exception {
        var uri = URI.create(magnetLink);
        if (!"magnet".equalsIgnoreCase(uri.getScheme()) || !uri.getRawSchemeSpecificPart().toLowerCase().contains("xt=urn:btih:")) {
            throw new IllegalArgumentException("Solo se admiten magnet links con un infohash BitTorrent");
        }
        if (!updateState.claimDownload(updateId)) return false;
        try {
            torrentDownload.addMagnet(magnetLink);
            return true;
        } catch (Exception exception) {
            updateState.releaseDownloadClaim(updateId);
            throw exception;
        }
    }
}
