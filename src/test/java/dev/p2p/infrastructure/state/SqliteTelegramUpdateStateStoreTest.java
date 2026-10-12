package dev.p2p.infrastructure.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.p2p.domain.TorrentResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqliteTelegramUpdateStateStoreTest {
    @Test
    void persistsOffsetAndDownloadClaimsAcrossInstances() {
        String url = "jdbc:sqlite:file:telegram-state-test?mode=memory&cache=shared";
        var first = new SqliteTelegramUpdateStateStore(url);
        first.advanceOffset(15L);
        assertTrue(first.claimDownload(14L));

        var second = new SqliteTelegramUpdateStateStore(url);

        assertEquals(15L, second.nextOffset());
        assertFalse(second.claimDownload(14L));
        second.releaseDownloadClaim(14L);
        assertTrue(second.claimDownload(14L));
        second.close();
        first.close();
    }

    @Test
    void persistsSelectionsUntilTheirTtlExpires() throws Exception {
        String url = "jdbc:sqlite:file:selection-state-test?mode=memory&cache=shared";
        var first = new SqliteTelegramUpdateStateStore(url, 1);
        first.saveSelection(42L, List.of(new TorrentResult("Ubuntu", "magnet:?xt=urn:btih:abc")));
        var second = new SqliteTelegramUpdateStateStore(url, 1);

        assertEquals(List.of(new TorrentResult("Ubuntu", "magnet:?xt=urn:btih:abc")), second.loadSelection(42L));

        Thread.sleep(1_100L);
        assertTrue(second.loadSelection(42L).isEmpty());
        second.close();
        first.close();
    }
}
