package fr.inra.oresing.rest;

import fr.inra.oresing.persistence.OperationReferenceType;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class AuthorizationsReferencesResult {
    Map<OperationReferenceType,List<String>> authorizationResults;
    String applicationName;
    Boolean isAdministrator;

}