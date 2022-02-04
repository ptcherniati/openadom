package fr.inra.oresing.groovy;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.ReferenceValueRepository;
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
        Map<String, List<ReferenceValue>> references = new HashMap<>();
        Map<String, List<Map<String, String>>> referencesValues = new HashMap<>();
        refs.forEach(ref -> {
            List<ReferenceValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(ref);
            references.put(ref, allByReferenceType);
            allByReferenceType.stream()
                    .map(ReferenceValue::getRefValues)
                    .forEach(values -> referencesValues.computeIfAbsent(ref, k -> new LinkedList<>()).add(values));
        });
        return ImmutableMap.<String, Object>builder()
                .put("references", references)
                .put("referencesValues", referencesValues)
                .build();
    }
}
