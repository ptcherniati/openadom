package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.file.FileOrUUID;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Objects;

public record ApplicationDepositWriterUser(
        Application application,
        String dataName,
        List<AuthorizationParsed> authorizations
) implements ApplicationDataWriter {
    @Override
    public boolean canDelete(FileOrUUID fileOrUUID) {
        return false;
    }

    @Override
    public boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID) {
        return true;
    }

    @Override
    public boolean hasRightForDeposit(FileOrUUID fileOrUUID) {
        if (isData()) {
            if (CollectionUtils.isEmpty(authorizations())) {
                throw getException();
            }
            return true;
        }
        return application().getConfiguration().findData(dataName())
                .flatMap(StandardDataDescription::findSubmissionScope)
                .filter(Objects::nonNull)
                .map(submissionScope -> {
                    if(fileOrUUID==null) {
                        throw new NotApplicationDataWriterForDepositException(applicationName(), dataName());
                    }

                    return testAuthorizationParsed(authorizations(), fileOrUUID);
                })
                .orElse(true);
    }

    @Override
    public NotApplicationDataWriterForDepositException getException() {
        return new NotApplicationDataWriterForDepositException(applicationName(), dataName());
    }
}