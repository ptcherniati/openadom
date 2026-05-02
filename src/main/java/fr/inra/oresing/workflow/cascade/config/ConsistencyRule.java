package fr.inra.oresing.workflow.cascade.config;

import java.util.Map;
import java.util.Optional;

/**
 * Regle de coherence appliquee sur l'etat hypothetique post-patch .
 * Implementations declarent un severite : {@link Severity#BLOCKING} =
 * patch rejete avec 400 ; {@link Severity#WARNING} = patch applique
 * mais l'UI affiche un avertissement non-bloquant .
 *
 * <p>Pattern strategy : ajouter une regle se fait en creant une
 * implementation et en l'inscrivant dans le registry Spring
 * automatique ( bean de meme interface ) . Chaque regle est isolee ,
 * testable individuellement , et composable avec les autres .
 *
 * @author R.YAHIAOUI
 */
public interface ConsistencyRule {

    enum Severity { BLOCKING, WARNING }

    /**
     * Identifiant unique de la regle ( utile pour les logs , l'audit ,
     * et la suppression future d'une regle obsolete ) .
     */
    String code();

    /** Severite : bloquante ( 400 ) ou avertissement ( applique + warn ) . */
    Severity severity();

    /**
     * Evalue la regle sur l'etat hypothetique post-patch . Retourne
     * {@link Optional#empty()} si la regle est respectee , ou un
     * message explicatif si elle est violee .
     *
     * @param effective vue immutable de l'etat config apres
     *                  application du patch ( current values
     *                  overriden by patch entries )
     */
    Optional<String> check(EffectiveConfig effective);

    /**
     * Vue immutable de la config post-patch , passee aux regles .
     * Encapsule les valeurs accessibles par nom symbolique pour que
     * les regles n'aient pas besoin de connaitre la representation
     * physique ( volatile fields , ConfigField , etc. ) .
     */
    record EffectiveConfig(Map<String, Object> values) {
        public String stringValue(String key) {
            Object v = values.get(key);
            return v == null ? null : String.valueOf(v);
        }
        public boolean boolValue(String key) {
            Object v = values.get(key);
            return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
        }
    }
}
