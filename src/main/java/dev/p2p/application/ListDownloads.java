package dev.p2p.application;

import dev.p2p.application.port.out.DownloadStatusPort;
import dev.p2p.domain.DownloadStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ListDownloads {
    private final DownloadStatusPort downloadStatus;

    @Inject
    public ListDownloads(DownloadStatusPort downloadStatus) {
        this.downloadStatus = downloadStatus;
    }

    public List<DownloadStatus> list() throws Exception {
        return downloadStatus.listDownloads();
    }
}
