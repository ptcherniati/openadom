package fr.inra.oresing.workflow.cascade.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Ring buffer in-memory des modifications de configuration appliquees
 * par les admins via l'endpoint d'edition live . Pas de persistence : la
 * liste est repartie a vide a chaque redemarrage ( c'est voulu pour
 * Phase 1 - on ajoutera une table {@code oa_audit.config_history}
 * en Phase 2 si besoin ) .
 *
 * <p>Capacite max {@link #MAX_ENTRIES} = 200 ( les plus recents en haut ) .
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class ConfigChangeAudit {

    public static final int MAX_ENTRIES = 200;

    public enum Status { APPLIED, REJECTED }

    private final Deque<Entry> entries = new LinkedList<>();

    public synchronized void record(String adminLogin, String field,
                                    String oldValue, String newValue,
                                    Status status, String message) {
        Entry e = new Entry(
                Instant.now(),
                adminLogin,
                field,
                oldValue,
                newValue,
                status,
                message);
        entries.addFirst(e);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        log.info("ConfigAudit : {} {} {} -> {} ( {} ) {}",
                adminLogin, field, oldValue, newValue, status,
                message == null ? "" : "[ " + message + " ]");
    }

    public synchronized List<Entry> list() {
        return new ArrayList<>(entries).stream()
                .sorted(Comparator.comparing(Entry::time).reversed())
                .toList();
    }

    public synchronized int size() { return entries.size(); }

    public synchronized void clear() { entries.clear(); }

    public record Entry(
            Instant time,
            String  adminLogin,
            String  field,
            String  oldValue,
            String  newValue,
            Status  status,
            String  message) { }
}