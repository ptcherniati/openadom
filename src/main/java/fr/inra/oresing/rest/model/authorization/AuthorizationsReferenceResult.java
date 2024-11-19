package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.persistence.OperationReferenceType;

import java.util.List;
import java.util.Map;


public record AuthorizationsReferenceResult(Map<OperationReferenceType, List<String>> authorizationResults,
                                            String applicationName) {
}