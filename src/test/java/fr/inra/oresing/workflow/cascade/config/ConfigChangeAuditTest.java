package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigChangeAudit ring buffer")
class ConfigChangeAuditTest {

    @Test
    @DisplayName("recent first ordering")
    void recentFirst() throws InterruptedException {
        ConfigChangeAudit audit = new ConfigChangeAudit();
        audit.record("alice", "chunkSizeLines", "1000", "2000",
                ConfigChangeAudit.Status.APPLIED, null);
        Thread.sleep(2);
        audit.record("alice", "maxErrorsThreshold", "100", "50",
                ConfigChangeAudit.Status.APPLIED, null);
        Thread.sleep(2);
        audit.record("alice", "chunkSizeLines", "2000", "abc",
                ConfigChangeAudit.Status.REJECTED, "value out of range");

        List<ConfigChangeAudit.Entry> list = audit.list();
        assertEquals(3, list.size());
        assertEquals(ConfigChangeAudit.Status.REJECTED, list.get(0).status());
        assertEquals("maxErrorsThreshold", list.get(1).field());
        assertEquals("chunkSizeLines", list.get(2).field());
    }

    @Test
    @DisplayName("ring buffer cap evicts oldest")
    void ringBufferCap() {
        ConfigChangeAudit audit = new ConfigChangeAudit();
        for (int i = 0; i < ConfigChangeAudit.MAX_ENTRIES + 50; i++) {
            audit.record("alice", "f" + i, null, "v",
                    ConfigChangeAudit.Status.APPLIED, null);
        }
        assertEquals(ConfigChangeAudit.MAX_ENTRIES, audit.size());
        // oldest entries 0..49 doivent etre evictees ; le plus vieux restant = f50
        List<ConfigChangeAudit.Entry> list = audit.list();
        assertEquals("f" + (ConfigChangeAudit.MAX_ENTRIES + 49),
                list.get(0).field()); // most recent on top
    }

    @Test
    @DisplayName("clear empties buffer")
    void clear() {
        ConfigChangeAudit audit = new ConfigChangeAudit();
        audit.record("alice", "f", null, "v",
                ConfigChangeAudit.Status.APPLIED, null);
        assertEquals(1, audit.size());
        audit.clear();
        assertEquals(0, audit.size());
        assertTrue(audit.list().isEmpty());
    }
}
