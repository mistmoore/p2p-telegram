package dev.p2p.application.port.out;

public interface TorrentPresencePort {
    boolean hasInfoHash(String infoHash) throws Exception;
}
