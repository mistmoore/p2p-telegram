package dev.p2p.application.port.out;

import dev.p2p.domain.DownloadStatus;
import java.util.List;

public interface DownloadStatusPort {
    List<DownloadStatus> listDownloads() throws Exception;
}
