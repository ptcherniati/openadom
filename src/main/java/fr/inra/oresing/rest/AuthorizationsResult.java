package fr.inra.oresing.rest;

import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class AuthorizationsResult {
    Map<String, Map<OperationType,List<AuthorizationParsed>>> authorizationResults;
    String applicationName;
    Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> authorizationByPath;
    Boolean isAdministrator;

}