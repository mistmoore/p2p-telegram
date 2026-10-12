package dev.p2p.infrastructure.state;

import dev.p2p.application.port.out.TelegramUpdateStatePort;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@ApplicationScoped
public class SqliteTelegramUpdateStateStore implements TelegramUpdateStatePort {
    private final Connection connection;

    @Inject
    public SqliteTelegramUpdateStateStore(ApplicationConfiguration configuration) {
        this(configuration.stateDatabaseUrl());
    }

    public SqliteTelegramUpdateStateStore(String databaseUrl) {
        try {
            connection = DriverManager.getConnection(databaseUrl);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS telegram_state (id INTEGER PRIMARY KEY CHECK (id = 1), next_offset INTEGER NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS submitted_downloads (update_id INTEGER PRIMARY KEY)");
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

    @PreDestroy
    void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo cerrar SQLite", exception);
        }
    }
}
