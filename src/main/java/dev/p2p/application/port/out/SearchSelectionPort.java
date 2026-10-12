package dev.p2p.application.port.out;

import dev.p2p.domain.TorrentResult;
import java.util.List;

public interface SearchSelectionPort {
    void saveSelection(long chatId, List<TorrentResult> results);
    List<TorrentResult> loadSelection(long chatId);
}
