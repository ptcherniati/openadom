package fr.inra.oresing.domain.repository.data;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface DataRepositoryForBuffer {
    Map<String, Map<String, String>> findDisplayByReferenceType(String referenceType);

    Map<String, String> findDisplayByReferenceTypeAndNaturalKey(String referenceType, String naturalKey);

    String findDisplayByReferenceTypeAndNaturalKeyAndLocale(String referenceType, String naturalKey, String locale);

    Map<String, List<Ltree>> checkHierarchicalKey(Map<String, List<Ltree>> requiredAuthorizations) throws SiOreIllegalArgumentException;

    List<Ltree> getHierarchicalKeyForEntry(Map.Entry<String, List<Ltree>> requiredAuthorizationByReference);

    Stream<DataValue> findAllByReferenceTypeStream(String referenceName);
}
