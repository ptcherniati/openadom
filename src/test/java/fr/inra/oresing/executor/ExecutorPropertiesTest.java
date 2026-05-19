package fr.inra.oresing.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ExecutorProperties} — sans contexte Spring.
 * Vérifie les valeurs par défaut, les setters et les sous-configurations
 * de chaque pool d'exécuteur.
 */
@Tag("core.config")
@DisplayName("ExecutorProperties — valeurs par défaut et setters")
class ExecutorPropertiesTest {

    // ─── valeurs par défaut globales ────────────────────────────────────────

    @Test
    @DisplayName("useVirtualThreads est true par défaut")
    void defaultUseVirtualThreads() {
        ExecutorProperties props = new ExecutorProperties();
        assertTrue(props.isUseVirtualThreads());
    }

    @Test
    @DisplayName("awaitTermination est true par défaut")
    void defaultAwaitTermination() {
        ExecutorProperties props = new ExecutorProperties();
        assertTrue(props.isAwaitTermination());
    }

    @Test
    @DisplayName("awaitTerminationSeconds vaut 30 par défaut")
    void defaultAwaitTerminationSeconds() {
        ExecutorProperties props = new ExecutorProperties();
        assertEquals(30, props.getAwaitTerminationSeconds());
    }

    @Test
    @DisplayName("setUseVirtualThreads modifie la valeur")
    void setUseVirtualThreads() {
        ExecutorProperties props = new ExecutorProperties();
        props.setUseVirtualThreads(false);
        assertFalse(props.isUseVirtualThreads());
        props.setUseVirtualThreads(true);
        assertTrue(props.isUseVirtualThreads());
    }

    @Test
    @DisplayName("setAwaitTermination modifie la valeur")
    void setAwaitTermination() {
        ExecutorProperties props = new ExecutorProperties();
        props.setAwaitTermination(false);
        assertFalse(props.isAwaitTermination());
    }

    @Test
    @DisplayName("setAwaitTerminationSeconds modifie la valeur")
    void setAwaitTerminationSeconds() {
        ExecutorProperties props = new ExecutorProperties();
        props.setAwaitTerminationSeconds(60);
        assertEquals(60, props.getAwaitTerminationSeconds());
    }

    // ─── sous-configs non-null ───────────────────────────────────────────────

    @Test
    @DisplayName("getFast() retourne une instance non-null")
    void getFastNotNull() {
        assertNotNull(new ExecutorProperties().getFast());
    }

    @Test
    @DisplayName("getNormal() retourne une instance non-null")
    void getNormalNotNull() {
        assertNotNull(new ExecutorProperties().getNormal());
    }

    @Test
    @DisplayName("getHeavy() retourne une instance non-null")
    void getHeavyNotNull() {
        assertNotNull(new ExecutorProperties().getHeavy());
    }

    @Test
    @DisplayName("getBackup() retourne une instance non-null")
    void getBackupNotNull() {
        assertNotNull(new ExecutorProperties().getBackup());
    }

    // ─── pool Fast ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fast pool")
    class FastPool {

        private ExecutorProperties.Fast fast() {
            return new ExecutorProperties().getFast();
        }

        @Test
        @DisplayName("valeurs par défaut")
        void defaults() {
            ExecutorProperties.Fast f = fast();
            assertEquals(200, f.getCorePoolSize());
            assertEquals(200, f.getMaxPoolSize());
            assertEquals(Integer.MAX_VALUE, f.getQueueCapacity());
            assertEquals("fast-", f.getThreadNamePrefix());
        }

        @Test
        @DisplayName("setters fonctionnent")
        void setters() {
            ExecutorProperties.Fast f = fast();
            f.setCorePoolSize(50);
            f.setMaxPoolSize(100);
            f.setQueueCapacity(500);
            f.setThreadNamePrefix("my-fast-");

            assertEquals(50, f.getCorePoolSize());
            assertEquals(100, f.getMaxPoolSize());
            assertEquals(500, f.getQueueCapacity());
            assertEquals("my-fast-", f.getThreadNamePrefix());
        }

        @Test
        @DisplayName("implémente PoolConfig")
        void implementsPoolConfig() {
            assertInstanceOf(ExecutorProperties.PoolConfig.class, fast());
        }
    }

    // ─── pool Normal ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Normal pool")
    class NormalPool {

        private ExecutorProperties.Normal normal() {
            return new ExecutorProperties().getNormal();
        }

        @Test
        @DisplayName("valeurs par défaut")
        void defaults() {
            ExecutorProperties.Normal n = normal();
            assertEquals(100, n.getCorePoolSize());
            assertEquals(100, n.getMaxPoolSize());
            assertEquals(1000, n.getQueueCapacity());
            assertEquals("normal-", n.getThreadNamePrefix());
        }

        @Test
        @DisplayName("setters fonctionnent")
        void setters() {
            ExecutorProperties.Normal n = normal();
            n.setCorePoolSize(20);
            n.setMaxPoolSize(40);
            n.setQueueCapacity(200);
            n.setThreadNamePrefix("my-normal-");
            assertEquals(20, n.getCorePoolSize());
            assertEquals(40, n.getMaxPoolSize());
            assertEquals(200, n.getQueueCapacity());
            assertEquals("my-normal-", n.getThreadNamePrefix());
        }
    }

    // ─── pool Heavy ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Heavy pool")
    class HeavyPool {

        private ExecutorProperties.Heavy heavy() {
            return new ExecutorProperties().getHeavy();
        }

        @Test
        @DisplayName("valeurs par défaut")
        void defaults() {
            ExecutorProperties.Heavy h = heavy();
            assertEquals(50, h.getCorePoolSize());
            assertEquals(50, h.getMaxPoolSize());
            assertEquals(200, h.getQueueCapacity());
            assertEquals("heavy-", h.getThreadNamePrefix());
        }

        @Test
        @DisplayName("setters fonctionnent")
        void setters() {
            ExecutorProperties.Heavy h = heavy();
            h.setCorePoolSize(10);
            h.setMaxPoolSize(20);
            h.setQueueCapacity(100);
            h.setThreadNamePrefix("my-heavy-");
            assertEquals(10, h.getCorePoolSize());
            assertEquals(20, h.getMaxPoolSize());
            assertEquals(100, h.getQueueCapacity());
            assertEquals("my-heavy-", h.getThreadNamePrefix());
        }
    }

    // ─── pool Backup ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Backup pool")
    class BackupPool {

        private ExecutorProperties.Backup backup() {
            return new ExecutorProperties().getBackup();
        }

        @Test
        @DisplayName("valeurs par défaut")
        void defaults() {
            ExecutorProperties.Backup b = backup();
            assertEquals(50, b.getCorePoolSize());
            assertEquals(50, b.getMaxPoolSize());
            assertEquals(500, b.getQueueCapacity());
            assertEquals("backup-", b.getThreadNamePrefix());
        }

        @Test
        @DisplayName("setters fonctionnent")
        void setters() {
            ExecutorProperties.Backup b = backup();
            b.setCorePoolSize(5);
            b.setMaxPoolSize(10);
            b.setQueueCapacity(50);
            b.setThreadNamePrefix("my-backup-");
            assertEquals(5, b.getCorePoolSize());
            assertEquals(10, b.getMaxPoolSize());
            assertEquals(50, b.getQueueCapacity());
            assertEquals("my-backup-", b.getThreadNamePrefix());
        }
    }
}
