package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationDataWriter;
import fr.inra.oresing.domain.file.FileOrUUID;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public class AuthorizationPublicationServiceBuilder {

    private AuthorizationPublicationServiceBuilder() {
    }

    public static StoredFileBuilder builder(final Application application,
                                            final String dataName,
                                            String fileName,
                                            FileOrUUID fileOrUUID,
                                            ApplicationDataWriter applicationDataWriter,
                                            Function<UUID, Optional<BinaryFile>> resolveFileById) {
        AuthorizationPublicationService builder = new AuthorizationPublicationService(
                application,
                dataName,
                deserialiseFileOrUUIDQuery(dataName, fileOrUUID, resolveFileById),
                applicationDataWriter);
        boolean hasSubmissionScope = application.findData(dataName)
                .map(StandardDataDescription::submission)
                .map(Submission::submissionScope)
                .map(Submission.SubmissionScope::referenceScopes)
                .map(List::size)
                .orElse(-1) > 0;
        boolean hasNoFileId = Optional.of(builder)
                .map(AuthorizationPublicationService::getFileOrUUID)
                .isEmpty();
        if (hasNoFileId && hasSubmissionScope) {
            final DatePattern submissionDatePattern = application.findSubmissionDatePattern(dataName);
            return new FileNameResolver(builder)
                    .resolveFileName(fileName, submissionDatePattern.pattern());
        }
        return new StoredFileBuilder(builder);
    }

    private static FileOrUUID deserialiseFileOrUUIDQuery(
            final String datatype,
            final FileOrUUID fileOrUUID,
            Function<UUID, Optional<BinaryFile>> resolveFileById) {
        if (fileOrUUID == null) {
            return null;
        }

        Optional.ofNullable(fileOrUUID)
                .map(FileOrUUID::binaryfiledataset)
                .ifPresent(binaryFileDataset -> {
                    String resolvedDatatype = Optional.of(binaryFileDataset)
                            .map(BinaryFileDataset::getDatatype)
                            .filter(Predicate.not(String::isBlank))
                            .orElse(datatype);
                    binaryFileDataset.setDatatype(resolvedDatatype);
                });
        boolean isNotDefinedDatatype = Optional.ofNullable(fileOrUUID)
                .map(FileOrUUID::binaryfiledataset)
                .map(BinaryFileDataset::getDatatype).isEmpty();
        if (isNotDefinedDatatype) {
            Optional<UUID> uuid = Optional.ofNullable(fileOrUUID)
                    .map(FileOrUUID::fileid);
            if (uuid.isEmpty()) {
                return fileOrUUID;
            }
            return uuid
                    .map(resolveFileById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(BinaryFile::getParams)
                    .map(fileOrUUID::withParams)
                    .orElseThrow(IllegalArgumentException::new);
        }
        return fileOrUUID;
    }
}