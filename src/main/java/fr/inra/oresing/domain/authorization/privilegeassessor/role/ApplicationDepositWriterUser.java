package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;

public record ApplicationDepositWriterUser(
        Application application,
        String dataName,
        ArrayList<AuthorizationParsed> authorizations
) implements ApplicationDataWriter {
    @Override
    public boolean canDelete(FileOrUUID fileOrUUID) {
        return false;
    }

    @Override
    public boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID) {
        return false;
    }

    @Override
    public boolean hasRightForDeposit(FileOrUUID fileOrUUID) {
        if(!isData()){
            if(CollectionUtils.isEmpty(authorizations)){
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
        if(!isDateInRangeAuthorized(fileOrUUID.binaryfiledataset(), authorizationParseds)){
            throw getException();
        }
        return true;
    }

    @Override
    public NotApplicationDataWriterForDepositException getException() {
        return new NotApplicationDataWriterForDepositException(applicationName(), dataName());
    }
}
