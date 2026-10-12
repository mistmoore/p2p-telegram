package dev.p2p.application.port.out;

public interface TorrentDownloadPort {
    void addMagnet(String magnetLink) throws Exception;
}
