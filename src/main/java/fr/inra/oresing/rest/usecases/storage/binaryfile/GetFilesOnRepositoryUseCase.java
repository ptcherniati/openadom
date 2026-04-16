package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetFilesOnRepositoryUseCase {

    private final BinaryFileService binaryFileService;

    public GetFilesOnRepositoryUseCase(BinaryFileService binaryFileService) {
        this.binaryFileService = binaryFileService;
    }

    public List<BinaryFile> execute(String nameOrId, String dataType, BinaryFileDataset binaryFileDataset, boolean includeData) {
        return binaryFileService.getFilesOnRepository(nameOrId, dataType, binaryFileDataset, includeData);
    }
}
