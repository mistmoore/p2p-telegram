package dev.p2p.application.port.out;

public interface TelegramUpdateStatePort {
    long nextOffset();
    void advanceOffset(long nextOffset);
    boolean claimDownload(long updateId);
    void releaseDownloadClaim(long updateId);
}
