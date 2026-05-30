package fr.inra.oresing.workflow.guard;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Garde-fou de résilience : levée quand un import de référentiel
 * <b>récursif</b> dépasse le nombre de lignes autorisé.
 *
 * <p>Un référentiel récursif accumule tout le fichier en mémoire JVM pendant
 * le transform ( index naturalKey→hierarchicalKey , map des UUID , lignes à
 * parent manquant différées ) pour résoudre les liens parent→enfant.
 * Au-delà d'un certain volume la heap déborde et la JVM tombe en
 * {@code OutOfMemoryError} , ce qui ferait crasher le backend pour TOUS les
 * utilisateurs. On rejette donc proprement l'import en amont plutôt que de
 * laisser la JVM mourir.
 *
 * <p>Annotée {@code @ResponseStatus(PAYLOAD_TOO_LARGE)} → Spring renvoie un
 * 413 quand l'exception remonte du controller. Le plafond est configurable
 * ( {@code cascade.import.recursive-max-rows} , 0 = désactivé ) ; le message
 * invite à découper le fichier.
 */
@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class RecursiveReferenceImportTooLargeException extends RuntimeException {

    public RecursiveReferenceImportTooLargeException(String refType, long rows, long maxRows) {
        super("Le référentiel récursif '" + refType + "' compte " + rows
                + " lignes , au-delà de la limite de " + maxRows + " lignes pour les"
                + " référentiels récursifs ( accumulation mémoire ) . Découpez le fichier"
                + " en plusieurs dépôts plus petits , ou demandez à un administrateur de"
                + " relever cascade.import.recursive-max-rows .");
    }
}
