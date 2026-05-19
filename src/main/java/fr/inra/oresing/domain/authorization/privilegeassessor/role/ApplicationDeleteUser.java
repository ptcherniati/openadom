package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCanDeleteRightsException;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Optional;

public record ApplicationDeleteUser(
        Application application,
        String dataName,
        List<AuthorizationParsed> authorizations,
        boolean isRepository
) implements ApplicationDataDelete, ApplicationDataWriter {
    public ApplicationDeleteUser(Application application, String dataName, List<AuthorizationParsed> authorizations) {
        this(application, dataName, authorizations, application.findSubmission(dataName).isPresent());
    }

    @Override
    public boolean canDelete(FileOrUUID fileOrUUID) {
        List<AuthorizationParsed> authorizationParseds = authorizations().stream()
                .filter(authorizationParsed -> Optional.ofNullable(authorizationParsed)
                        .map(AuthorizationParsed::operationTypes)
                        .stream().anyMatch(operationTypes -> operationTypes.contains(OperationType.delete))
                )
                .filter(authorizationParsed -> testRequiredAuthorizations(authorizationParsed.requiredAuthorizations(), fileOrUUID.binaryfiledataset().getRequiredAuthorizations()))
                .toList();
        if (authorizationParseds.isEmpty()) {
            throw getException();
        }
        if (isDateInRangeAuthorized(fileOrUUID.binaryfiledataset(), authorizationParseds)) {
            return true;
        }
        throw getException();
    }

    @Override
    public boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID) {
        List<AuthorizationParsed> authorizationParseds = authorizations().stream()
                .filter(authorizationParsed -> Optional.ofNullable(authorizationParsed)
                        .map(AuthorizationParsed::operationTypes)
                        .stream().anyMatch(operationTypes -> operationTypes.contains(OperationType.delete))
                )
                .filter(authorizationParsed -> testRequiredAuthorizations(authorizationParsed.requiredAuthorizations(), fileOrUUID.binaryfiledataset().getRequiredAuthorizations()))
                .toList();
        if (!isRepository()) {
            if (CollectionUtils.isEmpty(authorizationParseds)) {
                throw getException();
            }
            return false;
        }
        if (authorizationParseds.isEmpty()) {
            throw getException();
        }
        if (isDateInRangeAuthorized(fileOrUUID.binaryfiledataset(), authorizationParseds)) {
            return true;
        }
        throw getException();
    }

    @Override
    public boolean hasRightForDeposit(FileOrUUID fileOrUUID) {
        return false;
    }

    public OreSiTechnicalException getException() {
        return new NotApplicationCanDeleteRightsException(applicationName(), dataName());
    }
}