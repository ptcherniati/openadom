package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.user.file.UserRepository;
import fr.inra.oresing.domain.services.authorization.AuthorizationService;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;

import java.util.Map;
import java.util.Set;

public record AuthorizationForUserBuilder(
        AuthorizationPublicationService builder
) implements State {

    public StoreFile buildAuthorizationForUserService(
            UserRepository userRepository,
            AuthorizationService authorizationService) {
        CurrentUserRoles currentUserRoles = userRepository.getRolesForCurrentUser();
        boolean isUserManager = currentUserRoles.userManagerOf(application());
        builder().authorizationsForUser = authorizationService
                .getAuthorizationsForUserAndPublic(
                        application().getName(),
                        currentUserRoles.userLogin()
                );
        boolean isRepository = builder.isRepository(builder.application, builder.dataName);
        builder().authorizations =
                new AuthorizationForUser(
                        isUserManager,
                        isRepository,
                        canDeposit(isRepository, isUserManager),
                        canPublishOrUnPublish(isRepository, isUserManager),
                        canDelete(isRepository, isUserManager),
                        builder().authorizationsForUser,
                        builder()
                );
        return new StoreFile(builder());

    }

    private boolean canDeposit(boolean isRepository, boolean isApplicationCreator) {
        try {
            return hasRightForDeposit(
                    isRepository,
                    isApplicationCreator,
                    builder().authorizationsForUser
            );
        } catch (SiOreIllegalArgumentException e) {
            return false;
        }
    }

    private boolean canDelete(boolean isRepository, boolean isApplicationCreator) {
        try {
            return hasRightForDelete(
                    isRepository,
                    isApplicationCreator,
                    builder().authorizationsForUser
            );
        } catch (SiOreIllegalArgumentException e) {
            return false;
        }
    }

    private boolean canPublishOrUnPublish(boolean isRepository, boolean isApplicationCreator) {
        try {
            return hasRightForPublishOrUnPublish(
                    isRepository,
                    isApplicationCreator,
                    builder().authorizationsForUser,
                    builder().authorizationsForPublic
            );
        } catch (SiOreIllegalArgumentException e) {
            return false;
        }
    }

    private boolean canDoOperation(
            boolean isRepository,
            boolean isApplicationCreator,
            AuthorizationsResult authorizationsForUserOrPublic,
            OperationType operationType,
            final String errorMessage) {
        OperationType operationType1 = operationType;
        SiOreIllegalArgumentException siOreIllegalArgumentException = new SiOreIllegalArgumentException(errorMessage, Map.of("dataName", dataName(), "application", application().getName()));
        if (!isRepository && OperationType.depot == operationType1) {
            operationType1 = OperationType.publication;
        }
        boolean hasRightForOperationType = hasRightForOperationType(
                isApplicationCreator,
                authorizationsForUserOrPublic,
                operationType1
        );
        if (isRepository) {
            if (hasRightForOperationType) {
                return true;
            }
            throw siOreIllegalArgumentException;
        }
        final OperationType finalOperationType = operationType1;
        if (
                isApplicationCreator
                        || (hasRightForOperationType &&
                        testPredicateForOperationType(finalOperationType, this::testRequiredAuthorization))) {
            return true;
        }
        throw siOreIllegalArgumentException;
    }

    private boolean testRequiredAuthorization(AuthorizationParsed parsedAuhorization) {
        Map<String, Set<String>> requiredAuthorizationsInDatabase = parsedAuhorization.requiredAuthorizations();
        if (requiredAuthorizationsInDatabase.isEmpty()) {
            return true;
        } else {
            return params().requiredAuthorizationMatchForFile(requiredAuthorizationsInDatabase);
        }
    }

    private boolean hasRightForOperationType(
            boolean isApplicationCreator,
            AuthorizationsResult authorization,
            final OperationType operationType) {
        return isApplicationCreator
                || testPredicateForOperationType(operationType, auth -> true);
    }

    private boolean hasRightForDelete(
            boolean isRepository,
            boolean isApplicationCreator,
            AuthorizationsResult authorizationsForUser) {
        return canDoOperation(
                isRepository,
                isApplicationCreator,
                authorizationsForUser,
                OperationType.delete,
                SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_DELETE);
    }

    private boolean hasRightForPublishOrUnPublish(
            boolean isRepository,
            boolean isApplicationCreator,
            AuthorizationsResult authorizationsForUser,
            AuthorizationsResult authorizationsForPublic) {
        return canDoOperation(
                isRepository,
                isApplicationCreator,
                authorizationsForUser,
                OperationType.publication,
                SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_PUBLISH_OR_UNPUBLISH);
    }

    private boolean hasRightForDeposit(
            boolean isRepository,
            boolean isApplicationCreator,
            AuthorizationsResult authorizationsForUser) {
        return canDoOperation(
                isRepository,
                isApplicationCreator,
                authorizationsForUser,
                OperationType.depot,
                SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_DEPOSIT
        );
    }
}