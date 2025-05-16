package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.BinaryFileInfos;

import java.util.Optional;

public record FileNameResolver(
        AuthorizationPublicationService builder) implements State {
    public StoredFileBuilder resolveFileName(String fileName) {
        BinaryFileDataset binaryFileDataset = fileOrUuid() == null ? new BinaryFileDataset() : fileOrUuid().binaryfiledataset();
        BinaryFileDataset resolvedBinaryFileDataset = Optional.ofNullable(dataDescription())
                .map(StandardDataDescription::submission)
                .map(submission -> submission.parseFileName(
                        fileName,
                        binaryFileDataset))
                .orElse(binaryFileDataset);
        FileOrUUID params = builder.fileOrUUID;
        if (params != null) {
            builder.fileOrUUID = params.withParams(new BinaryFileInfos(resolvedBinaryFileDataset));
        } else {
            builder.fileOrUUID = new FileOrUUID(null, resolvedBinaryFileDataset, true);
        }
        return new StoredFileBuilder(builder());
    }
}