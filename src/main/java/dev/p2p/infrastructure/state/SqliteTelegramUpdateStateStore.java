package dev.p2p.infrastructure.state;

import dev.p2p.application.port.out.TelegramUpdateStatePort;
import dev.p2p.application.port.out.SearchSelectionPort;
import dev.p2p.domain.TorrentResult;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SqliteTelegramUpdateStateStore implements TelegramUpdateStatePort, SearchSelectionPort {
    private final Connection connection;
    private final long selectionTtlMillis;

    @Inject
    public SqliteTelegramUpdateStateStore(ApplicationConfiguration configuration) {
        this(configuration.stateDatabaseUrl(), configuration.selectionTtlSeconds());
    }

    public SqliteTelegramUpdateStateStore(String databaseUrl) {
        this(databaseUrl, 3600);
    }

    public SqliteTelegramUpdateStateStore(String databaseUrl, long selectionTtlSeconds) {
        try {
            selectionTtlMillis = Math.multiplyExact(selectionTtlSeconds, 1000L);
            connection = DriverManager.getConnection(databaseUrl);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS telegram_state (id INTEGER PRIMARY KEY CHECK (id = 1), next_offset INTEGER NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS submitted_downloads (update_id INTEGER PRIMARY KEY)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS search_selections (chat_id INTEGER NOT NULL, position INTEGER NOT NULL, title TEXT NOT NULL, link TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (chat_id, position))");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo inicializar el estado SQLite", exception);
        }
    }

    @Override
    public synchronized long nextOffset() {
        try (var statement = connection.prepareStatement("SELECT next_offset FROM telegram_state WHERE id = 1"); var result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo leer el offset de Telegram", exception);
        }
    }

    @Override
    public synchronized void advanceOffset(long nextOffset) {
        try (var statement = connection.prepareStatement("INSERT INTO telegram_state (id, next_offset) VALUES (1, ?) ON CONFLICT(id) DO UPDATE SET next_offset = MAX(next_offset, excluded.next_offset)")) {
            statement.setLong(1, nextOffset);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo guardar el offset de Telegram", exception);
        }
    }

    @Override
    public synchronized boolean claimDownload(long updateId) {
        try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO submitted_downloads (update_id) VALUES (?)")) {
            statement.setLong(1, updateId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo registrar la descarga", exception);
        }
    }

    @Override
    public synchronized void releaseDownloadClaim(long updateId) {
        try (var statement = connection.prepareStatement("DELETE FROM submitted_downloads WHERE update_id = ?")) {
            statement.setLong(1, updateId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo liberar la descarga", exception);
        }
    }

    @Override
    public synchronized void saveSelection(long chatId, List<TorrentResult> results) {
        try {
            connection.setAutoCommit(false);
            try (var delete = connection.prepareStatement("DELETE FROM search_selections WHERE chat_id = ?"); var insert = connection.prepareStatement("INSERT INTO search_selections (chat_id, position, title, link, created_at) VALUES (?, ?, ?, ?, ?)")) {
                delete.setLong(1, chatId);
                delete.executeUpdate();
                long createdAt = System.currentTimeMillis();
                for (int position = 0; position < results.size(); position++) {
                    var result = results.get(position);
                    insert.setLong(1, chatId);
                    insert.setInt(2, position);
                    insert.setString(3, result.title());
                    insert.setString(4, result.link());
                    insert.setLong(5, createdAt);
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo guardar la selección de búsqueda", exception);
        }
    }

    @Override
    public synchronized List<TorrentResult> loadSelection(long chatId) {
        long expiresAt = System.currentTimeMillis() - selectionTtlMillis;
        try (var delete = connection.prepareStatement("DELETE FROM search_selections WHERE chat_id = ? AND created_at < ?"); var select = connection.prepareStatement("SELECT title, link FROM search_selections WHERE chat_id = ? ORDER BY position")) {
            delete.setLong(1, chatId);
            delete.setLong(2, expiresAt);
            delete.executeUpdate();
            select.setLong(1, chatId);
            try (var rows = select.executeQuery()) {
                var results = new ArrayList<TorrentResult>();
                while (rows.next()) results.add(new TorrentResult(rows.getString("title"), rows.getString("link")));
                return List.copyOf(results);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo leer la selección de búsqueda", exception);
        }
    }

    @PreDestroy
    void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo cerrar SQLite", exception);
        }
    }
}
