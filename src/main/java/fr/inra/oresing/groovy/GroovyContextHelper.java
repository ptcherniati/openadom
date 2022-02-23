package fr.inra.oresing.groovy;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ReferenceDatum;
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
        Map<String, List<ReferenceValueDecorator>> references = new HashMap<>();
        Map<String, List<Map<String, Object>>> referencesValues = new HashMap<>();
        refs.forEach(ref -> {
            List<ReferenceValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(ref);
            allByReferenceType.stream()
                    .map(ReferenceValueDecorator::new)
                    .forEach(referenceValue -> references.computeIfAbsent(ref, k -> new LinkedList<>()).add(referenceValue));
            allByReferenceType.stream()
                    .map(ReferenceValue::getRefValues)
                    .map(ReferenceDatum::fromDatabaseJson)
                    .forEach(values -> referencesValues.computeIfAbsent(ref, k -> new LinkedList<>()).add(values.toObjectsExposedInGroovyContext()));
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

    /**
     * On expose pas directement les entités dans le contexte Groovy mais on contrôle un peu les types
     */
    private static class ReferenceValueDecorator {

        private final ReferenceValue decorated;

        public ReferenceValueDecorator(ReferenceValue decorated) {
            this.decorated = decorated;
        }

        public String getHierarchicalKey() {
            return decorated.getHierarchicalKey().getSql();
        }

        public String getHierarchicalReference() {
            return decorated.getHierarchicalReference().getSql();
        }

        public String getNaturalKey() {
            return decorated.getNaturalKey().getSql();
        }

        public Map<String, Object> getRefValues() {
            return ReferenceDatum.fromDatabaseJson(decorated.getRefValues()).toObjectsExposedInGroovyContext();
        }
    }
}
