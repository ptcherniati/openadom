package fr.inra.oresing.rest;

import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class AuthorizationsResult {
    Map<OperationType,List<AuthorizationParsed>> authorizationResults;
    String applicationName;
    String dataType;

}