package fr.inra.oresing.domain.repository.data;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.persistence.DataRows;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public interface DataRepository {
    @Transactional(readOnly = true)
    ImmutableMap<DataValue.LineIdentityPatternColumnName, UUID> getDataIdPerKeys(String s);

    @Transactional(readOnly = true)
    Stream<DataValue> findAllByReferenceTypeStream(String ref);

    @Transactional(readOnly = true)
    Stream<DataValue> findAllByReferenceTypeWithReferencingReferencesStream(final String refType, final MultiValueMap<String, String> params);

    int removeByFileId(UUID id);

    Map<String, List<Ltree>> resolveRequiredAuthorizations(Map<String, List<Ltree>> stringLtreeMap);

    Map<String, String> findHierarchicalKeysByKeyForReferenceTypes(List<String> referenceType);

    Flux<DataRows> findAllByDataTypeFlux(DownloadDatasetQuery downloadDatasetQuery);

    List<ReferenceScope.NodeDescription> getNodesForMenu(MenuType menuType);

    Flux<FileContent> getStoredData(String dataName, SubmissionType submissionType);
}
