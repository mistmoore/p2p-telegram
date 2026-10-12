package dev.p2p.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class ApplicationConfigurationTest {
    @Test
    void acceptsCompleteLocalConfiguration() {
        assertDoesNotThrow(() -> new ApplicationConfiguration("token", 42L, "http://localhost:9117", "key", "http://localhost:8081", "admin", "password"));
    }

    @Test
    void rejectsMissingRequiredSecrets() {
        assertThrows(IllegalStateException.class, () -> new ApplicationConfiguration("", 42L, "http://localhost:9117", "key", "http://localhost:8081", "admin", "password"));
    }

    @Test
    void rejectsNonHttpServiceUrls() {
        assertThrows(IllegalStateException.class, () -> new ApplicationConfiguration("token", 42L, "ftp://localhost", "key", "http://localhost:8081", "admin", "password"));
    }
}
