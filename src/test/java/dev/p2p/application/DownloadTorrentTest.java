package dev.p2p.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import dev.p2p.application.port.out.TorrentDownloadPort;
import org.junit.jupiter.api.Test;

class DownloadTorrentTest {
    @Test
    void delegatesValidMagnetLinks() throws Exception {
        var download = new CapturingDownload();

        new DownloadTorrent(download).download("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567");

        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", download.magnetLink);
    }

    @Test
    void rejectsHttpUrlsBeforeTheDownloadAdapter() {
        var download = new CapturingDownload();

        assertThrows(IllegalArgumentException.class, () -> new DownloadTorrent(download).download("http://127.0.0.1:8080/admin"));
    }

    private static final class CapturingDownload implements TorrentDownloadPort {
        private String magnetLink;

        @Override
        public void addMagnet(String magnetLink) {
            this.magnetLink = magnetLink;
        }
    }
}
