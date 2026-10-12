package dev.p2p.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import dev.p2p.application.port.out.TorrentDownloadPort;
import dev.p2p.application.port.out.TelegramUpdateStatePort;
import org.junit.jupiter.api.Test;

class DownloadTorrentTest {
    @Test
    void delegatesValidMagnetLinks() throws Exception {
        var download = new CapturingDownload();

        new DownloadTorrent(download, new InMemoryState(), infoHash -> false).download(1L, "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567");

        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", download.magnetLink);
    }

    @Test
    void delegatesTorrentUrlsToTheDownloadAdapter() throws Exception {
        var download = new CapturingDownload();

        new DownloadTorrent(download, new InMemoryState(), infoHash -> false).download(1L, "http://127.0.0.1:9117/dl/indexer/file.torrent");

        assertEquals("http://127.0.0.1:9117/dl/indexer/file.torrent", download.torrentUrl);
    }

    @Test
    void retriesAnAmbiguousClaimWhenQbittorrentDoesNotHaveTheInfoHash() throws Exception {
        var download = new CapturingDownload();
        var state = new InMemoryState();
        state.claimDownload(1L);

        boolean submitted = new DownloadTorrent(download, state, infoHash -> false).download(1L, "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567");

        assertEquals(true, submitted);
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", download.magnetLink);
    }

    @Test
    void doesNotResubmitAnAmbiguousClaimWhenQbittorrentHasTheInfoHash() throws Exception {
        var download = new CapturingDownload();
        var state = new InMemoryState();
        state.claimDownload(1L);

        boolean submitted = new DownloadTorrent(download, state, infoHash -> true).download(1L, "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567");

        assertEquals(false, submitted);
        assertEquals(null, download.magnetLink);
    }

    @Test
    void reportsWhenQbittorrentAlreadyHadTheTorrent() throws Exception {
        var download = new CapturingDownload();
        download.added = false;

        boolean submitted = new DownloadTorrent(download, new InMemoryState(), infoHash -> false).download(1L, "http://127.0.0.1:9117/dl/indexer/file.torrent");

        assertEquals(false, submitted);
        assertEquals("http://127.0.0.1:9117/dl/indexer/file.torrent", download.torrentUrl);
    }

    private static final class CapturingDownload implements TorrentDownloadPort {
        private String magnetLink;
        private String torrentUrl;
        private boolean added = true;

        @Override
        public boolean addMagnet(String magnetLink) {
            this.magnetLink = magnetLink;
            return added;
        }

        @Override
        public boolean addTorrentFile(String torrentUrl) {
            this.torrentUrl = torrentUrl;
            return added;
        }
    }

    private static final class InMemoryState implements TelegramUpdateStatePort {
        private boolean claimed;

        @Override public long nextOffset() { return 0; }
        @Override public void advanceOffset(long nextOffset) { }
        @Override public boolean claimDownload(long updateId) { if (claimed) return false; claimed = true; return true; }
        @Override public void releaseDownloadClaim(long updateId) { claimed = false; }
    }
}
