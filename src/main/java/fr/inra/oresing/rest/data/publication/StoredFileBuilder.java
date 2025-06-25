package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;

import java.util.Optional;

public record StoredFileBuilder(
        AuthorizationPublicationService builder
) implements State {

    public StoreFile testAndBuild(DataRepository dataRepository) {
        Optional.ofNullable(builder())
                .map(AuthorizationPublicationService::getFileOrUUID)
                .map(FileOrUUID::binaryfiledataset)
                .map(binaryFileDataset -> binaryFileDataset.testrequiredAuthorizationsAndReturnHierarchicalKeys(dataRepository))
                .map(BinaryFileDataset::getRequiredAuthorizations)
                .ifPresent(requiredAuthorization -> builder().fileOrUUID.binaryfiledataset().setRequiredAuthorizations(requiredAuthorization));
        return new StoreFile(builder()).testRights();
    }
}