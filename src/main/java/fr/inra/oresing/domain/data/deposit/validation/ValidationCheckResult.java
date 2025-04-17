package fr.inra.oresing.domain.data.deposit.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;

import java.util.*;
import java.util.stream.Collectors;

public interface ValidationCheckResult {

    int MAX_COLLECTION_SIZE = 12;

    ValidationLevel level();

    String message();

    Map<String, Object> messageParams();

    @JsonIgnore
    default boolean isSuccess() {
        return level().isSuccess();
    }

    @JsonIgnore
    default boolean isError() {
        return level().isError();
    }

    CheckerTarget target();

    @JsonIgnore
    default List<ValidationCheckResult> getValidations() {
        return List.of(this);
    }


    default ValidationCheckResultRest validationCheckResultToRest(long lineNumber) {
        return new ValidationCheckResultRest(
                getClass().getSimpleName(),
                message(),
                filterParamsLength(messageParams()),
                lineNumber
        );
    }

    default Map<String, Object> filterParamsLength(Map<String, Object> params) {
        if (params == null) {
            return null;
        }

        return params.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> filterValue(entry.getValue())
                ));
    }

    default Object filterValue(Object value) {
        if (value instanceof Collection<?> collection) {
            if(collection.size()<=MAX_COLLECTION_SIZE){
                return collection;
            }
            List<?> limitedList = collection.stream()
                    .limit(MAX_COLLECTION_SIZE)
                    .collect(Collectors.toList());
            if (value instanceof List) {
                return limitedList;
            } else if (value instanceof Set) {
                return new HashSet<>(limitedList);
            } else if (value instanceof SortedSet) {
                return new TreeSet<>(limitedList);
            } else {
                return limitedList; // Retourne une List par défaut
            }
        } else if (value instanceof Map<?, ?> map) {
            Map<?, ?> limitedMap = map.entrySet().stream()
                    .limit(MAX_COLLECTION_SIZE)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (value instanceof SortedMap) {
                return new TreeMap<>(limitedMap);
            } else {
                return limitedMap;
            }
        } else {
            return value;
        }
    }
}

