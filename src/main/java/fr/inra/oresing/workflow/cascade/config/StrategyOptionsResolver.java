package fr.inra.oresing.workflow.cascade.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resoud , pour chaque champ enum de la config , la liste des valeurs
 * autorisees etant donne l'etat hypothetique courant ( EffectiveConfig ) .
 *
 * <p>Strategie : pour chaque valeur possible d'un field enum , on
 * simule un patch ( field = value ) , on evalue les regles BLOCKING ,
 * et on conserve la valeur seulement si aucune regle ne bloque .
 *
 * <p>Permet a l'UI de griser les options non-applicables sans avoir
 * a dupliquer la logique cote frontend .
 *
 * @author R.YAHIAOUI
 */
@Component
@RequiredArgsConstructor
public class StrategyOptionsResolver {

    private final ConfigFieldRegistry registry;
    private final List<ConsistencyRule> rules;

    /**
     * Pour chaque field enum hot-editable , retourne la liste des
     * valeurs autorisees etant donne l'etat actuel ( apres patch
     * hypothetique ) . Les valeurs bloquees sont annotees avec leur
     * raison de blocage pour affichage UI .
     */
    public Map<String, List<Option>> resolve(Map<String, Object> effective) {
        Map<String, List<Option>> out = new LinkedHashMap<>();
        for (var meta : registry.schema()) {
            if ("enum".equals(meta.type()) && meta.hot() && meta.allowedValues() != null) {
                List<Option> opts = new ArrayList<>();
                for (String candidate : meta.allowedValues()) {
                    Map<String, Object> hypothetical = new LinkedHashMap<>(effective);
                    hypothetical.put(meta.name(), candidate);
                    String blockReason = firstBlockingReason(hypothetical);
                    opts.add(new Option(candidate, blockReason == null, blockReason));
                }
                out.put(meta.name(), opts);
            }
        }
        return out;
    }

    private String firstBlockingReason(Map<String, Object> hypothetical) {
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(hypothetical);
        for (ConsistencyRule rule : rules) {
            if (rule.severity() != ConsistencyRule.Severity.BLOCKING) continue;
            var msg = rule.check(cfg);
            if (msg.isPresent()) return msg.get();
        }
        return null;
    }

    /**
     * @param value     la valeur enum candidate
     * @param allowed   true si aucune regle BLOCKING ne s'applique
     * @param blockReason message explicatif quand allowed = false ; null sinon
     */
    public record Option(String value, boolean allowed, String blockReason) { }
}