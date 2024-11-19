package fr.inra.oresing.domain.repository.data;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public interface DataRepositoryForBuffer {
    Map<String, Map<String, String>> findDisplayByReferenceType(String referenceType);

    Map<String, String> findDisplayByReferenceTypeAndNaturalKey(String referenceType, String naturalKey);

    String findDisplayByReferenceTypeAndNaturalKeyAndLocale(String referenceType, String naturalKey, String locale);

    Map<String, List<Ltree>> checkHierarchicalKey(Map<String, List<Ltree>> requiredAuthorizations) throws SiOreIllegalArgumentException;
}
