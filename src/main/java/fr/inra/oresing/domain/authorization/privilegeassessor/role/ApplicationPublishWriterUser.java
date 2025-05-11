package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public record ApplicationPublishWriterUser(
        Application application,
        String dataName,
        ArrayList<AuthorizationParsed> authorizations
) implements ApplicationDataWriter {
    @Override
    public boolean canDelete(FileOrUUID fileOrUUID) {
        return true;
    }

    @Override
    public boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID) {
        if(isData()){
            if(CollectionUtils.isEmpty(authorizations())){
                throw getException();
            }
            return true;
        }
        List<AuthorizationParsed> authorizationParseds = authorizations().stream()
                .filter(authorizationParsed -> testRequiredAuthorizations(authorizationParsed.requiredAuthorizations(), fileOrUUID.binaryfiledataset().getRequiredAuthorizations()))
                .toList();
        if(authorizationParseds.isEmpty()){
            throw getException();
        }
        if(isDateInRangeAuthorized(fileOrUUID.binaryfiledataset(), authorizationParseds)){
            return true;
        }
        throw getException();
    }

    @Override
    public boolean hasRightForDeposit(FileOrUUID fileOrUUID) {
        return hasRightForPublishOrUnPublish(fileOrUUID);
    }

    public OreSiTechnicalException getException() {
        return new NotApplicationDataWriterForDepositException(applicationName(), dataName());
    }
}