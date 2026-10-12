package dev.p2p.domain;

public record DownloadStatus(String name, double progress, String state, long downloadSpeed, long etaSeconds) {
}
