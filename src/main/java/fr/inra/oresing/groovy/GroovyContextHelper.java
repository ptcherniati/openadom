package fr.inra.oresing.groovy;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import fr.inra.oresing.rest.DownloadDatasetQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class GroovyContextHelper {

    public ImmutableMap<String, Object> getGroovyContextForReferences(ReferenceValueRepository referenceValueRepository, Set<String> refs) {
        Map<String, List<Map<String, String>>> references = new HashMap<>();
        Map<String, List<Map<String, String>>> referencesValues = new HashMap<>();
        refs.forEach(ref -> {
            List<ReferenceValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(ref);
            allByReferenceType.stream()
                    .forEach(referenceValue -> referencesValues.computeIfAbsent(ref, k -> new LinkedList<>()).add(Map.of(
                            "hierarchicalKey", referenceValue.getHierarchicalKey(),
                            "hierarchicalReference", referenceValue.getHierarchicalReference(),
                            "naturalKey", referenceValue.getNaturalKey()
                    )));
            allByReferenceType.stream()
                    .map(ReferenceValue::getRefValues)
                    .forEach(values -> referencesValues.computeIfAbsent(ref, k -> new LinkedList<>()).add(values));
        });
        return ImmutableMap.<String, Object>builder()
                .put("references", references)
                .put("referencesValues", referencesValues)
                .build();
    }

    public ImmutableMap<String, Object> getGroovyContextForDataTypes(DataRepository dataRepository, Set<String> dataTypes, @Deprecated Application application) {
        Map<String, List<DataRow>> datatypes = new HashMap<>();
        Map<String, List<Map<String, Map<String, String>>>> datatypesValues = new HashMap<>();
        dataTypes.forEach(dataType -> {
            List<DataRow> allByDataType = dataRepository.findAllByDataType(DownloadDatasetQuery.buildDownloadDatasetQuery(null, null, dataType, application));
            datatypes.put(dataType, allByDataType);
            allByDataType.stream()
                    .map(datatValues -> datatValues.getValues())
                    .forEach(dv -> datatypesValues.computeIfAbsent(dataType, k -> new LinkedList<>()).add(dv));
        });
        return ImmutableMap.<String, Object>builder()
                .put("datatypes", datatypes)
                .put("datatypesValues", datatypesValues)
                .build();
    }
}
