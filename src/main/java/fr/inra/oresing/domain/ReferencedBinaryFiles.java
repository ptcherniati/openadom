package fr.inra.oresing.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReferencedBinaryFiles(
        UUID binaryFileId,
        String dataType,
        Map<String, List<UUID>> referencedBinaryFileIdsByReferencetype
) {
}