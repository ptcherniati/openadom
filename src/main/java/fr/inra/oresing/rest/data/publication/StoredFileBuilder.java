package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record StoredFileBuilder(
        AuthorizationPublicationService builder
) implements State {

    public StoreFile testAndBuild(DataRepositoryForBuffer dataRepositoryWithBuffer) {
        Map<String, List<Ltree>> requiredAuthorization = Optional.ofNullable(builder())
                .map(AuthorizationPublicationService::getFileOrUUID)
                .map(FileOrUUID::binaryfiledataset)
                .map(binaryFileDataset -> binaryFileDataset.testrequiredAuthorizationsAndReturnHierarchicalKeys(dataRepositoryWithBuffer))
                .map(BinaryFileDataset::getRequiredAuthorizations)
                .orElse(null);
        if (requiredAuthorization != null) {
            builder().fileOrUUID.binaryfiledataset().setRequiredAuthorizations(requiredAuthorization);
        }
        return new StoreFile(builder()).testRights();
    }
}