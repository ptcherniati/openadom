package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.rest.OreSiResources;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import org.springframework.web.util.UriUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultat d un cycle de versioning ( create / unpublish / delete ) .
 *
 * <p>{@link #dataSynthesis} reflete le compteur de lignes au moment ou le
 * record a ete construit . En mode cascade 3.0.0 deferred , le UPSERT
 * staging -> table finale tourne dans {@code afterCommit} du caller ;
 * tant qu il n a pas fire le compteur lu en DB est encore stale . Pour
 * exposer un compteur frais a l appelant , VersioningService renvoie
 * d abord un resultat avec {@code dataSynthesis = List.of()} , puis le
 * caller ( typiquement la couche REST , post {@code Future.get()} ) appelle
 * {@code VersioningService.finalizePostCommit} qui recalcule la synthese
 * et la replace via {@link #withDataSynthesis} avant de la propager dans
 * la reponse HTTP / le mail de notification .
 *
 * <p>{@link #uploadState} expose au caller le type d operation effectuee
 * ( PUBLISHED / UPLOADED / UNPUBLISHED / DELETED ) , necessaire pour
 * router le bon template de mail post-commit . Nullable pour les flows
 * qui n envoient pas de notification ( ex. delete admin direct ) .
 */
public record DataVersioningResult(UUID dataId,
                                    List<ApplicationResult.DataSynthesis> dataSynthesis,
                                    String uri,
                                    EmailService.UPLOAD_STATE uploadState) {

    public DataVersioningResult {
        Objects.requireNonNull(dataId);
        dataSynthesis = dataSynthesis == null ? List.of() : List.copyOf(dataSynthesis);
    }

    /** Constructeur historique sans uploadState ( back-compat ) . */
    public DataVersioningResult(UUID dataId, List<ApplicationResult.DataSynthesis> dataSynthesis, String uri) {
        this(dataId, dataSynthesis, uri, null);
    }

    public static DataVersioningResult of(String nameOrId, String dataName, UUID dataId, List<ApplicationResult.DataSynthesis> dataSynthesis) {
        return of(nameOrId, dataName, dataId, dataSynthesis, null);
    }

    public static DataVersioningResult of(String nameOrId, String dataName, UUID dataId,
                                           List<ApplicationResult.DataSynthesis> dataSynthesis,
                                           EmailService.UPLOAD_STATE uploadState) {
        final String uri = UriUtils.encodePath(String.format(OreSiResources.DATA_SERVICE_PATH_PATTERN, nameOrId, dataName), Charset.defaultCharset());
        return new DataVersioningResult(dataId, dataSynthesis, uri, uploadState);
    }

    /**
     * Retourne une copie avec le {@code dataSynthesis} remplace . Utilise
     * par le caller pour injecter le compteur frais lu post-commit ( quand
     * le UPSERT differe a fini ) sans muter ce record immuable .
     */
    public DataVersioningResult withDataSynthesis(List<ApplicationResult.DataSynthesis> fresh) {
        return new DataVersioningResult(dataId, fresh, uri, uploadState);
    }
}