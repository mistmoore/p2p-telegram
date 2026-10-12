package dev.p2p.infrastructure.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
