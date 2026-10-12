package dev.p2p.adapter.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.p2p.application.DownloadTorrent;
import dev.p2p.application.ListDownloads;
import dev.p2p.application.SearchTorrents;
import dev.p2p.application.port.out.TelegramUpdateStatePort;
import dev.p2p.application.port.out.SearchSelectionPort;
import dev.p2p.domain.TorrentResult;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import dev.p2p.infrastructure.http.HttpGateway;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TelegramBot {
    private static final Logger LOG = Logger.getLogger(TelegramBot.class);
    private final HttpGateway http;
    private final ObjectMapper mapper;
    private final SearchTorrents searchTorrents;
    private final DownloadTorrent downloadTorrent;
    private final ListDownloads listDownloads;
    private final ApplicationConfiguration configuration;
    private final TelegramUpdateStatePort updateState;
    private final SearchSelectionPort searchSelection;
    private long offset;

    @Inject
    public TelegramBot(HttpGateway http, ObjectMapper mapper, SearchTorrents searchTorrents, DownloadTorrent downloadTorrent, ListDownloads listDownloads, ApplicationConfiguration configuration, TelegramUpdateStatePort updateState, SearchSelectionPort searchSelection) {
        this.http = http;
        this.mapper = mapper;
        this.searchTorrents = searchTorrents;
        this.downloadTorrent = downloadTorrent;
        this.listDownloads = listDownloads;
        this.configuration = configuration;
        this.updateState = updateState;
        this.searchSelection = searchSelection;
        this.offset = updateState.nextOffset();
    }

    private String api(String method) { return "https://api.telegram.org/bot" + configuration.telegramToken() + "/" + method; }

    private void send(long chat, String message) throws Exception {
        http.post(api("sendMessage"), Map.of("chat_id", String.valueOf(chat), "text", message), null);
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        try {
            var response = http.get(api("getUpdates") + "?timeout=25&offset=" + offset + "&limit=20", Duration.ofSeconds(35));
            var payload = mapper.readTree(response.body());
            if (!payload.path("ok").asBoolean()) throw new IllegalStateException("Telegram rechazó getUpdates");
            for (JsonNode update : payload.path("result")) {
                long updateId = update.path("update_id").asLong();
                JsonNode message = update.path("message");
                if (message.isMissingNode()) {
                    acknowledge(updateId);
                    continue;
                }
                long user = message.path("from").path("id").asLong();
                if (user != configuration.telegramAllowedUser() || !"private".equals(message.path("chat").path("type").asText())) {
                    acknowledge(updateId);
                    continue;
                }
                long chat = message.path("chat").path("id").asLong();
                try {
                    handle(chat, updateId, message.path("text").asText("").trim());
                    acknowledge(updateId);
                } catch (Exception exception) {
                    LOG.warn("Bot command failed", exception);
                    send(chat, "No se pudo completar la operación. Revisa la configuración y los logs.");
                    acknowledge(updateId);
                }
            }
        } catch (Exception exception) {
            LOG.warn("Telegram polling failed", exception);
        }
    }

    private void acknowledge(long updateId) {
        offset = Math.max(offset, updateId + 1);
        updateState.advanceOffset(offset);
    }

    private void handle(long chat, long updateId, String input) throws Exception {
        if (input.startsWith("/buscar ")) {
            String query = input.substring(8).trim();
            if (query.isBlank() || query.length() > 120) {
                send(chat, "Consulta no válida (máximo 120 caracteres).");
                return;
            }
            var results = searchTorrents.search(query);
            searchSelection.saveSelection(chat, results);
            if (results.isEmpty()) {
                send(chat, "Sin resultados.");
                return;
            }
            var text = new StringBuilder("Resultados (elige con /descargar N):\n");
            for (int i = 0; i < results.size(); i++) text.append(i + 1).append(". ").append(truncate(results.get(i).title(), 180)).append("\n");
            send(chat, text.substring(0, Math.min(text.length(), 4096)));
        } else if (input.matches("/descargar \\d+")) {
            int index = Integer.parseInt(input.substring(11).trim()) - 1;
            var results = searchSelection.loadSelection(chat);
            if (index < 0 || index >= results.size()) {
                send(chat, "Número inválido. Usa /buscar primero.");
                return;
            }
            if (downloadTorrent.download(updateId, results.get(index).link())) send(chat, "Descarga enviada a qBittorrent.");
            else send(chat, "Esta descarga ya fue enviada a qBittorrent.");
        } else if (input.equals("/descargas")) {
            var downloads = listDownloads.list();
            if (downloads.isEmpty()) {
                send(chat, "No hay descargas activas ni completadas.");
                return;
            }
            var text = new StringBuilder("Descargas:\n");
            for (var download : downloads.stream().limit(20).toList()) {
                text.append(truncate(download.name(), 120)).append("\n")
                    .append(String.format(Locale.ROOT, "%.1f%% · %s · %s · ETA %s\n", download.progress() * 100, download.state(), formatSpeed(download.downloadSpeed()), formatEta(download.etaSeconds())));
                if (text.length() > 3800) break;
            }
            send(chat, text.toString());
        } else if (input.equals("/start") || input.equals("/ayuda")) {
            send(chat, "Comandos: /buscar texto · /descargar N · /descargas · /ayuda");
        } else {
            send(chat, "Usa /buscar texto para buscar archivos.");
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private static String formatSpeed(long bytesPerSecond) {
        return String.format(Locale.ROOT, "%.1f KiB/s", bytesPerSecond / 1024.0);
    }

    private static String formatEta(long seconds) {
        if (seconds < 0 || seconds >= 8_640_000L) return "?";
        return "%dh %02dm".formatted(seconds / 3600, (seconds % 3600) / 60);
    }
}
