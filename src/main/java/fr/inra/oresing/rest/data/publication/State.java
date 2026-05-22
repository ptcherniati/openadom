package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.file.FileOrUUID;

import java.util.function.Predicate;

sealed public interface State permits StoredFileBuilder, CheckAndStoreFile, FileNameResolver, JustStoredFile, StoreFile, UnPublishedVersions {


    Predicate<AuthorizationParsed> TRUE_PREDICATE = auth -> true;

    AuthorizationPublicationService builder();

    default BinaryFile binaryFile() {
        return builder().binaryFile;
    }

    default boolean isRepository() {
        return builder().isRepository();
    }

    default StandardDataDescription dataDescription() {
        return builder().dataDescription;
    }

    default Application application() {
        return builder().application;
    }

    default String dataName() {
        return builder().dataName;
    }

    default FileOrUUID fileOrUuid() {
        return builder().fileOrUUID;
    }
}