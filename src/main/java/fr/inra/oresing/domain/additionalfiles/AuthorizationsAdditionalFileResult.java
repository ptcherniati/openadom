package fr.inra.oresing.domain.additionalfiles;

import fr.inra.oresing.domain.additionalfiles.OperationAdditionalFileType;

import java.util.List;
import java.util.Map;

public record AuthorizationsAdditionalFileResult(Map<OperationAdditionalFileType, List<String>> authorizationResults,
                                                 String applicationName) {
}