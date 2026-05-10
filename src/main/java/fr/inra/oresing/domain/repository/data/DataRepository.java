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