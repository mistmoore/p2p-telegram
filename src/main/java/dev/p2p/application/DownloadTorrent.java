package dev.p2p.application;

import dev.p2p.application.port.out.TorrentDownloadPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;

@ApplicationScoped
public class DownloadTorrent {
    private final TorrentDownloadPort torrentDownload;

    @Inject
    public DownloadTorrent(TorrentDownloadPort torrentDownload) {
        this.torrentDownload = torrentDownload;
    }

    public void download(String magnetLink) throws Exception {
        var uri = URI.create(magnetLink);
        if (!"magnet".equalsIgnoreCase(uri.getScheme()) || !uri.getRawSchemeSpecificPart().toLowerCase().contains("xt=urn:btih:")) {
            throw new IllegalArgumentException("Solo se admiten magnet links con un infohash BitTorrent");
        }
        torrentDownload.addMagnet(magnetLink);
    }
}
