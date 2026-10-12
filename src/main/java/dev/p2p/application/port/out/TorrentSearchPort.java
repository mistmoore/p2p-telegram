package dev.p2p.application.port.out;

import dev.p2p.domain.TorrentResult;
import java.util.List;

public interface TorrentSearchPort {
    List<TorrentResult> search(String query) throws Exception;
}
