package dev.p2p.application.port.out;

public interface TorrentDownloadPort {
    /** Devuelve {@code false} cuando qBittorrent ya tenía ese torrent. */
    boolean addMagnet(String magnetLink) throws Exception;

    /** Devuelve {@code false} cuando qBittorrent ya tenía ese torrent. */
    boolean addTorrentFile(String torrentUrl) throws Exception;
}
