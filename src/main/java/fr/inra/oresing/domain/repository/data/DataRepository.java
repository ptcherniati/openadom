package fr.inra.oresing.domain.repository.data;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataRows;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.bundle.BundleFileContent;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Port domaine pour l'accès aux données.
 *
 * <p>Interface pure : aucune annotation Spring, aucun type persistence.
 * Les implémentations ({@code persistence.DataRepository}) portent
 * les annotations {@code @Transactional}, {@code @Repository}, etc.
 */
public interface DataRepository {
    ImmutableMap<DataValue.LineIdentityColumnName, UUID> getDataIdPerKeys(String s);

    Stream<DataValue> findAllByReferenceTypeStream(String referenceName);

    List<DataValue> findAllByReferenceType(String reference);

    Stream<DataValue> findAllByReferenceTypeWithReferencingReferencesStream(
            final String refType, final Map<String, List<String>> params);

    /**
     * Charge le fichier CSV final ( produit par le pipeline cascade ) dans
     * la table cible via COPY + INSERT … ON CONFLICT.
     *
     * @return nombre de rows effectivement upsertees dans la table finale
     *         ( somme des {@code executeUpdate} sur chaque batch ) . Sert
     *         a l'instrumentation : {@link fr.inra.oresing.workflow.cascade.StoreAllPathSink}
     *         remonte cette valeur a {@code CascadeImportPipeline} pour
     *         que {@code workflow_log.records_processed} reflete le nombre
     *         reel de rows ecrites en finale ( la valeur cascade
     *         {@code result.recordsProcessed()} compte les chunks emis par
     *         le sink = 1 dans le cas MERGE_FILE , trompeuse pour
     *         {@code IntegrityService} ) .
     */
    default long storeAll(Path finalCsvFile) {
        return storeAll(finalCsvFile, n -> {}, phase -> {});
    }

    /**
     * Variante instrumentee de {@link #storeAll(Path)} . Permet a
     * {@code StoreAllPathSink} d'observer l'avancement du chemin
     * MERGE_FILE en 3 phases pour alimenter la live view :
     *
     * <ul>
     *   <li>Phase {@code MERGE_LOCAL} : declenchee avant le COPY ( les
     *       rows sont deja merges sur disque par le collector cascade ;
     *       cette phase est instantanee cote DB ) .</li>
     *   <li>Phase {@code TEMP_LOAD} : COPY merged.csv vers la TEMP TABLE
     *       referencevalue_import . Indeterminate cote UI ( 1 statement ) .</li>
     *   <li>Phase {@code UPSERT_FINAL} : loop batche UPSERT TEMP -> table
     *       finale . Determinate via {@code onBatchUpserted} appele apres
     *       chaque batch avec le rowcount affecte .</li>
     * </ul>
     *
     * @param finalCsvFile      fichier merged.csv produit par cascade
     * @param onBatchUpserted   callback appele apres chaque batch UPSERT
     *                          avec le nombre de rows affected ( cumulable
     *                          cote consommateur ) . Doit etre rapide ( appel
     *                          synchrone dans la transaction ) .
     * @param onPhaseChange     callback appele au changement de sous-phase
     *                          ( valeurs : MERGE_LOCAL , TEMP_LOAD ,
     *                          UPSERT_FINAL ) . Permet au sink de marquer
     *                          la transition pour la barre indeterminate
     *                          en phase B .
     * @return nombre total de rows upsertees dans la table finale
     */
    long storeAll(Path finalCsvFile,
                  java.util.function.LongConsumer onBatchUpserted,
                  java.util.function.Consumer<String> onPhaseChange);

    void removeByFileId(UUID id);

    /**
     * Chunked variant of {@link #removeByFileId} : DELETE the rows by
     * id-batches with per-chunk progress + cooperative cancellation .
     * Required at scale ( 1M+ rows ) to avoid {@code statement_timeout} ,
     * WAL pressure and unobservable EN_ATTENTE periods .
     *
     * @param fileId      binaryfile uuid to wipe
     * @param chunkSize   rows per batch ( typical 10_000 )
     * @param onProgress  optional callback ( cumulative rows deleted ) ;
     *                    fires after each chunk for live UI bar
     * @param cancelCheck optional cooperative cancel between chunks ;
     *                    throws {@link java.util.concurrent.CancellationException}
     *                    if returns true ; partial DELETE remains committed
     * @return total rows deleted from referencevalue
     */
    long removeByFileIdChunked(UUID fileId,
                               int chunkSize,
                               java.util.function.LongConsumer onProgress,
                               java.util.function.BooleanSupplier cancelCheck);

    /**
     * Returns the number of {@code referencevalue} rows currently linked
     * to {@code fileId} . Used to populate {@code workflow_log.records_total}
     * BEFORE the DELETE during an unpublish / delete-file flow so oa-live
     * can render the "Lignes" column and the progress bar immediately .
     * Uses the {@code referencevalue_binaryfile_idx} btree index ; cost
     * negligible even on 100M+ row tables .
     */
    long countByFileId(UUID fileId);

    /**
     * Variante lazy de {@link #getDataIdPerKeys(String)} : ne charge que
     * les naturalkeys donnees en parametre ( typiquement extraites du CSV
     * en cours de publication via le pre-scan ) . Borne la memoire a
     * O(M_referenced) au lieu de O(N_ref_size) ; permet de scaler aux
     * referentiels 100M+ rows en BDD sans OOM cote Java .
     *
     * @param referenceType         refType cible
     * @param naturalKeysOfInterest set des naturalkeys ( format texte
     *                              compatible ltree ) ; vide / null = no-op
     * @return mapping bornee a {@code naturalKeysOfInterest.size()} entrees
     *         max ; vide si aucune nk ne matche
     */
    com.google.common.collect.ImmutableMap<fr.inra.oresing.domain.data.DataValue.LineIdentityColumnName, UUID>
            getDataIdPerKeysByNaturalKeys(String referenceType,
                                          java.util.Set<String> naturalKeysOfInterest);

    Map<String, List<Ltree>> resolveRequiredAuthorizations(Map<String, List<Ltree>> stringLtreeMap);

    Map<String, String> findHierarchicalKeysByKeyForReferenceTypes(List<String> referenceType);

    Stream<DataRows> findAllByDataTypeStream(DownloadDatasetQuery downloadDatasetQuery);

    List<ReferenceScope.NodeDescription> getNodesForMenu(MenuType menuType);

    Stream<BundleFileContent> getStoredData(Application application, String dataName);

    void flush();

    Map<String, Map<String, String>> findDisplayByNaturalKey(String replace);

    /**
     * Ordre de tri pour les requêtes de données.
     */
    enum Order {
        ASC, DESC
    }
}