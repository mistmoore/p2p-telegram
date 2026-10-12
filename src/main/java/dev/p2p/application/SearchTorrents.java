package dev.p2p.application;

import dev.p2p.application.port.out.TorrentSearchPort;
import dev.p2p.domain.TorrentResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class SearchTorrents {
    private final TorrentSearchPort torrentSearch;

    @Inject
    public SearchTorrents(TorrentSearchPort torrentSearch) {
        this.torrentSearch = torrentSearch;
    }

    public List<TorrentResult> search(String query) throws Exception {
        return torrentSearch.search(query);
    }
}
