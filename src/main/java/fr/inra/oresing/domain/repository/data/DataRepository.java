package fr.inra.oresing.domain.repository.data;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.persistence.DataRows;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public interface DataRepository {
    @Transactional(readOnly = true)
    ImmutableMap<DataValue.LineIdentityColumnName, UUID> getDataIdPerKeys(String s);

    @Transactional(readOnly = true)
    Stream<DataValue> findAllByReferenceTypeStream(String referenceName);
    @Transactional(readOnly = true)
    List<DataValue> findAllByReferenceType(String reference);

    @Transactional(readOnly = true)
    Stream<DataValue> findAllByReferenceTypeWithReferencingReferencesStream(final String refType, final MultiValueMap<String, String> params);

    /**
     * Charge le fichier CSV final ( produit par le pipeline cascade ) dans
     * la table cible via COPY + INSERT … ON CONFLICT.
     *
     * <p>Pas de retour : le RETURNING id de l'INSERT a ete supprime ( resultat
     * jamais utilise par les appelants -> economise serialisation /
     * transport / heap pour 280k+ rows par import ).
     */
    void storeAll(Path finalCsvFile);

    void removeByFileId(UUID id);

    Map<String, List<Ltree>> resolveRequiredAuthorizations(Map<String, List<Ltree>> stringLtreeMap);

    Map<String, String> findHierarchicalKeysByKeyForReferenceTypes(List<String> referenceType);

    Flux<DataRows> findAllByDataTypeFlux(DownloadDatasetQuery downloadDatasetQuery);

    List<ReferenceScope.NodeDescription> getNodesForMenu(MenuType menuType);

    Flux<FileContent> getStoredData(Application application, String dataName);

    void flush();

    Map<String, Map<String, String>> findDisplayByNaturalKey(String replace);
}