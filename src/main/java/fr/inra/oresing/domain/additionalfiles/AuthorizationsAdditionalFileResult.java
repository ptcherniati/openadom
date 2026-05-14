package fr.inra.oresing.domain.additionalfiles;

import java.util.List;
import java.util.Map;

public record AuthorizationsAdditionalFileResult(Map<OperationAdditionalFileType, List<String>> authorizationResults,
                                                 String applicationName) {
}