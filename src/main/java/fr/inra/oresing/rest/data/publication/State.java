package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

sealed public interface State permits AuthorizationForUser, AuthorizationForUserBuilder, CheckRights, FileNameResolver, JustStoredFile, ParamsResolver, StoreFile, UnPublishedVersions {


    public static final Predicate<AuthorizationParsed> TRUE_PREDICATE = auth -> true;
    AuthorizationPublicationService builder();

    default BinaryFile binaryFile() {
        return builder().binaryFile;
    }

    default boolean isApplicationCreator() {
        return builder().isApplicationCreator();
    }

    default boolean isRepository() {
        return builder().isRepository();
    }

    default Boolean canDeposit() {
        return builder().canDeposit();
    }

    default StandardDataDescription dataDescription() {
        return builder().dataDescription;
    }

    default AuthorizationsResult authorizationsForUserAndPublic() {
        return builder().authorizationsForUser();
    }

    default Application application() {
        return builder().application;
    }

    default String dataName() {
        return builder().dataName;
    }

    default FileOrUUID params() {
        return builder().params;
    }default boolean testPredicateForOperationType(OperationType operationType, Predicate<AuthorizationParsed> predicate) {
        // Check user authorizations
        boolean userAuth = Optional.ofNullable(authorizationsForUserAndPublic().userAuthorization().get(dataName()))
                .map(authList -> authList.stream()
                        .filter(auth -> auth.operationTypes().contains(operationType))
                        .anyMatch(predicate))
                .orElse(false);

        // Check public authorization
        boolean publicAuth = Optional.ofNullable(authorizationsForUserAndPublic().publicAuthorization().get(dataName()))
                .filter(auth -> auth.operationTypes().contains(operationType))
                .map(predicate::test)
                .orElse(false);

        return userAuth || publicAuth;
    }
}
