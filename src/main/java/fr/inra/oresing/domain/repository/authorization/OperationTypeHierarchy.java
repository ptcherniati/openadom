package fr.inra.oresing.domain.repository.authorization;

import java.util.EnumSet;
import java.util.Set;

/**
 * Helper qui normalise un Set&lt;OperationType&gt; selon les règles métier
 * du ticket #521 ( réponse Damien Maurice du 2026-05-25 ) .
 *
 * <p><b>Règles encodées ( hiérarchie stricte intra-ligne ) :</b>
 * <ul>
 *   <li>{@code depot} et {@code publication} sont la même opération métier :
 *       présence de l'une = présence de l'autre ( miroir auto pour préserver
 *       la compat backend / RLS sans toucher l'enum ni la BDD ) .</li>
 *   <li>{@code delete} implique {@code depot + publication + extraction} .</li>
 *   <li>{@code depot} ( ou {@code publication} ) implique {@code extraction} .</li>
 *   <li>{@code extraction} seule reste seule .</li>
 *   <li>{@code associate} et autres types laissés tels quels .</li>
 * </ul>
 *
 * <p><b>Combinaisons valides en sortie ( point de vue UI 3 colonnes ) :</b>
 * {@code []} , {@code [extraction]} , {@code [extraction, depot, publication]} ,
 * {@code [extraction, depot, publication, delete]} .
 *
 * <p><b>Auto-correction silencieuse :</b> tout payload partiel entrant ( ex
 * un client API qui envoie {@code [depot]} sans {@code extraction} ) est
 * complété vers le haut sans erreur , conformément à la décision Q3 du
 * ticket ( robustesse pour clients extérieurs , UI ne produit jamais ces
 * cas ) .
 *
 * <p><b>Magie versionning :</b> l'ancien comportement ( en mode versionning ,
 * cocher {@code depot} ou {@code publication} ajoutait automatiquement
 * {@code delete} ) est <b>retiré</b> ici : la nouvelle règle de Damien
 * impose que {@code delete} soit coché EXPLICITEMENT par l'utilisateur .
 * Le code historique est conservé en commentaire dans les call-sites
 * ( {@code AuthorizationInput.withRestrictionWithDependants} et
 * {@code CreateAuthorizationRequest.authorizationForAllWithDependants} ) ,
 * à supprimer après confirmation .
 *
 * <p>Pure : pas de side effect , pas d'IO , pas de dépendance Spring .
 * Testable unitairement sans mock .
 */
public final class OperationTypeHierarchy {

    private OperationTypeHierarchy() {
        // utilitaire pur
    }

    /**
     * Normalise un set d'opérations en appliquant la hiérarchie stricte
     * et le miroir {@code depot <-> publication} . Retourne un EnumSet
     * nouvellement alloué ( ne modifie pas l'entrée ) .
     *
     * @param input set d'opérations issu du payload utilisateur ( peut être
     *              {@code null} ou vide )
     * @return set normalisé , jamais {@code null}
     */
    public static Set<OperationType> normalize(final Set<OperationType> input) {
        if (input == null || input.isEmpty()) {
            return EnumSet.noneOf(OperationType.class);
        }
        final EnumSet<OperationType> result = EnumSet.noneOf(OperationType.class);
        for (final OperationType op : input) {
            if (op != null) {
                result.add(op);
            }
        }
        // delete implique depot + publication + extraction
        if (result.contains(OperationType.delete)) {
            result.add(OperationType.depot);
            result.add(OperationType.publication);
            result.add(OperationType.extraction);
        }
        // miroir auto depot <-> publication ( même opération métier )
        if (result.contains(OperationType.depot) || result.contains(OperationType.publication)) {
            result.add(OperationType.depot);
            result.add(OperationType.publication);
            // depot implique extraction
            result.add(OperationType.extraction);
        }
        return result;
    }
}
