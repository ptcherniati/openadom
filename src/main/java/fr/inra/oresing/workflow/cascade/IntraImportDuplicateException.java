package fr.inra.oresing.workflow.cascade;

/**
 * Levee par {@link IntraImportDuplicateDetector} quand des doublons de
 * clé naturelle ( {@code hierarchicalKey_uniqueness} ) sont détectés dans
 * la table de staging AVANT l'UPSERT vers la table finale , et que la
 * politique configurée est {@code FAIL} ( cf
 * {@link fr.inra.oresing.workflow.cascade.config.ImportProperties.IntraDuplicatePolicy} ) .
 *
 * <p>Levée avant toute mutation ( reference_reference rebuild + UPSERT ) ,
 * donc le finalize avorte sans aucun effet de bord : la transaction sticky
 * rollback et le staging reste intact ( nettoyé par le sweeper TTL ) .
 *
 * <p>But : remplacer le comportement opaque actuel ( soit une SQLSTATE
 * {@code 21000} « cannot affect row a second time » au milieu de l'UPSERT
 * si les doublons tombent dans le même batch , soit un merge silencieux si
 * ils tombent dans des batches différents ) par une erreur métier claire
 * listant les clés en conflit .
 *
 * @author R.YAHIAOUI
 */
public class IntraImportDuplicateException extends RuntimeException {

    public IntraImportDuplicateException(String message) {
        super(message);
    }
}
