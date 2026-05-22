package fr.inra.oresing.workflow.cascade.config;

import fr.inra.oresing.persistence.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mutation a chaud de la configuration cascade exposee par les endpoints
 * admin de oa-live . Delegue 100% du mapping field -> getter/setter au
 * {@link ConfigFieldRegistry} : ce service ne contient plus que la
 * logique transverse ( admin guard , audit log , cohérence cross-fields ) .
 *
 * @author R.YAHIAOUI
 */
@Service
@Slf4j
public class ConfigEditService {

    private final ConfigFieldRegistry registry;
    private final ConfigChangeAudit   audit;
    private final AuthenticationService authenticationService;
    private final List<ConsistencyRule> rules;

    public ConfigEditService(ConfigFieldRegistry registry,
                             ConfigChangeAudit audit,
                             AuthenticationService authenticationService,
                             List<ConsistencyRule> rules) {
        this.registry = registry;
        this.audit = audit;
        this.authenticationService = authenticationService;
        // Tri par severite : BLOCKING d'abord pour fail-fast lisibilite ,
        // WARNING ensuite . Stable pour ordre deterministe des messages .
        this.rules = rules.stream()
                .sorted(java.util.Comparator
                        .comparing((ConsistencyRule r) -> r.severity().ordinal())
                        .thenComparing(ConsistencyRule::code))
                .toList();
    }

    /**
     * Applique un patch ( map fieldName -> nouvelle valeur ) . Tous les
     * fields non presents dans la map sont ignores . Les rejets sont
     * loggues dans l'audit et propage en exception au caller .
     */
    public PatchResult applyPatch(Map<String, Object> patch) {
        requireAdmin();
        String admin = currentLogin();

        // Pre-validation : applique les regles BLOCKING sur l'etat
        // hypothetique post-patch . En cas de rejet , l'audit log
        // enregistre un REJECTED global et aucune valeur n'est touchee .
        ConsistencyRule.EffectiveConfig effective = effectiveAfterPatch(patch);
        List<String> blocking = applyRules(effective, ConsistencyRule.Severity.BLOCKING);
        if (!blocking.isEmpty()) {
            String msg = String.join(" ; ", blocking);
            audit.record(admin, "(patch validation)", null,
                    patch.toString(),
                    ConfigChangeAudit.Status.REJECTED, msg);
            throw new IllegalArgumentException(msg);
        }

        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            String field = e.getKey();
            Object value = e.getValue();
            if (value == null) continue;
            // Aplatir poolParallelism : si value est une Map, la pousse field-by-field
            if ("poolParallelism".equals(field) && value instanceof Map<?, ?> nested) {
                applyNestedPoolMap(admin, nested, changes);
                continue;
            }
            applyOne(admin, field, value, changes);
        }
        // Apres mutation : on re-evalue les WARNING sur l'etat reel pour
        // refleter les changements committed ( ex. directWriteParallel
        // toggle peut changer une regle ) .
        List<String> warnings = applyRules(
                new ConsistencyRule.EffectiveConfig(registry.snapshot()),
                ConsistencyRule.Severity.WARNING);
        return new PatchResult(registry.snapshot(), changes, warnings);
    }

    /**
     * Construit l'etat hypothetique en appliquant le patch par-dessus le
     * snapshot courant . Utilise pour evaluer les regles BLOCKING avant
     * mutation reelle .
     */
    private ConsistencyRule.EffectiveConfig effectiveAfterPatch(Map<String, Object> patch) {
        Map<String, Object> effective = new LinkedHashMap<>(registry.snapshot());
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            if (e.getValue() == null) continue;
            if ("poolParallelism".equals(e.getKey()) && e.getValue() instanceof Map<?, ?> nested) {
                for (Map.Entry<?, ?> p : nested.entrySet()) {
                    effective.put("pool." + String.valueOf(p.getKey()).toLowerCase(), p.getValue());
                }
            } else {
                effective.put(e.getKey(), e.getValue());
            }
        }
        return new ConsistencyRule.EffectiveConfig(effective);
    }

    /** Applique toutes les regles d'une severite donnee et collecte les messages . */
    private List<String> applyRules(ConsistencyRule.EffectiveConfig effective,
                                    ConsistencyRule.Severity severity) {
        List<String> out = new ArrayList<>();
        for (ConsistencyRule rule : rules) {
            if (rule.severity() != severity) continue;
            rule.check(effective).ifPresent(out::add);
        }
        return out;
    }

    private void applyNestedPoolMap(String admin, Map<?, ?> nested, List<Change> changes) {
        for (Map.Entry<?, ?> e : nested.entrySet()) {
            applyOne(admin, "pool." + String.valueOf(e.getKey()).toLowerCase(),
                    e.getValue(), changes);
        }
    }

    private void applyOne(String admin, String field, Object value, List<Change> changes) {
        try {
            ConfigField.Mutation<?> m = registry.apply(field, value);
            if (m.changed()) {
                audit.record(admin, field, m.oldString(), m.newString(),
                        ConfigChangeAudit.Status.APPLIED, null);
                changes.add(new Change(field, m.oldString(), m.newString()));
            }
        } catch (NoSuchElementException
                | UnsupportedOperationException
                | IllegalArgumentException
                | IllegalStateException ex) {
            audit.record(admin, field, null,
                    value == null ? null : value.toString(),
                    ConfigChangeAudit.Status.REJECTED, ex.getMessage());
            throw ex;
        }
    }

    public List<ConfigFieldRegistry.FieldMeta> schema() {
        return registry.schema();
    }

    public List<ConfigChangeAudit.Entry> auditList() {
        requireAdmin();
        return audit.list();
    }

    /** Snapshot lecture seule de toutes les valeurs courantes . */
    public Map<String, Object> snapshot() {
        return registry.snapshot();
    }

    // ----------------------------- internals -----------------------------

    private void requireAdmin() {
        if (!authenticationService.getCurrentUserRoles().isOpenAdomAdmin()) {
            throw new AccessDeniedException("Reserved to openAdomAdmin users");
        }
    }

    private String currentLogin() {
        return Optional.ofNullable(authenticationService.getCurrentUser())
                .map(u -> u.getLogin())
                .orElse("unknown");
    }


    public record Change(String field, String oldValue, String newValue) { }

    public record PatchResult(
            Map<String, Object> snapshot,
            List<Change> changes,
            List<String> warnings) { }
}