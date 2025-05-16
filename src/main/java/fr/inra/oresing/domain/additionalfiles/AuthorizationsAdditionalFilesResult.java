package fr.inra.oresing.domain.additionalfiles;

import fr.inra.oresing.persistence.OperationAdditionalFileType;

import java.util.List;
import java.util.Map;

public record AuthorizationsAdditionalFilesResult(Map<OperationAdditionalFileType, List<String>> authorizationResults,
                                                  String applicationName, Boolean isAdministrator) {
}