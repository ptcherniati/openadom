package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class GetReferencedBinaryFilesUseCase {

    private final BinaryFileService binaryFileService;

    public GetReferencedBinaryFilesUseCase(BinaryFileService binaryFileService) {
        this.binaryFileService = binaryFileService;
    }

    public List<ReferencedBinaryFiles> execute(UUID applicationId, String dataType, Set<UUID> fileIds) {
        return binaryFileService.getReferencedBinaryFiles(applicationId, dataType, fileIds);
    }
}
