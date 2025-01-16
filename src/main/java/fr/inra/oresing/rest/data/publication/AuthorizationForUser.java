package fr.inra.oresing.rest.data.publication;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record AuthorizationForUser(
        boolean isApplicationCreator,
        boolean isRepository,
        Boolean canDeposit,
        Boolean canPublishOrUnPublish,
        Boolean canDelete,

        AuthorizationsResult authorizationsForUserOrPublic,
        AuthorizationPublicationService builder) implements State {

    protected static Boolean hasRight(
            final Authorization authorization,
            final List<AuthorizationParsed> auths) {
        return auths.stream()
                .anyMatch(auth -> hasRight(authorization, auth));
    }

    protected static Boolean hasRight(final Authorization authorization,
                                      final AuthorizationParsed auth) {
        if (auth.fromDay() != null) {
            if (!authorization.getTimeScope().getRange().hasLowerBound()) {
                return false;
            } else if (authorization.getTimeScope().getRange().lowerEndpoint().isBefore(auth.fromDay().atStartOfDay())) {
                return false;
            }
        }
        if (auth.toDay() != null) {
            if (!authorization.getTimeScope().getRange().hasUpperBound()) {
                return false;
            } else if (!authorization.getTimeScope().getRange().upperEndpoint().isBefore(auth.toDay().atStartOfDay())) {
                return false;
            }
        }
        return auth.requiredAuthorizations().entrySet().stream()
                .allMatch(entry -> {
                    String datatype = entry.getKey();
                    Set<String> pathesForDatatype = entry.getValue();
                    List<String> authorizationsForDatatype = Optional.ofNullable(authorization.getRequiredAuthorizations())
                            .map(map -> map.get(datatype))
                            .map(ltrees->ltrees.stream().map(Ltree::getSql).toList())
                            .orElse(List.of());
                    return pathesForDatatype.stream()
                            .anyMatch(path -> Strings.isNullOrEmpty(path) || authorizationsForDatatype.stream().anyMatch(path::equals));
                });
    }

    protected boolean canDoOperation(
            OperationType operationType,
            final String dataName,
            final String errorMessage
    ) {
        OperationType operationType1 = operationType;
        SiOreIllegalArgumentException siOreIllegalArgumentException = new SiOreIllegalArgumentException(errorMessage, Map.of("dataName", dataName, "application", application().getName()));
        if (!isRepository && OperationType.depot == operationType1) {
            operationType1 = OperationType.publication;
        }
        boolean hasRightForOperationType = hasRightForOperationType(operationType1, dataName);
        if (isRepository) {
            if (hasRightForOperationType) {
                return true;
            }
            throw siOreIllegalArgumentException;
        }
        if (
                isApplicationCreator
                        || (hasRightForOperationType &&
                        testPredicateForOperationType(operationType,
                                this::testRequiredAuthorizations)
                )) {
            return true;
        }
        throw siOreIllegalArgumentException;
    }

    private boolean testRequiredAuthorizations(AuthorizationParsed parsedAuhorizations) {
        Map<String, Set<String>> requiredAuthorizationsInDatabase = parsedAuhorizations.requiredAuthorizations();
        if (requiredAuthorizationsInDatabase.isEmpty()) {
            return true;
        } else {
            return requiredAuthorizationMatchForFile(requiredAuthorizationsInDatabase);
        }
    }

    protected boolean requiredAuthorizationMatchForFile(
            final Map<String, Set<String>> requiredAuthorizationInDataBase) {
        Optional<Map<String, List<Ltree>>> requiredAuthorizationForFile = Optional.ofNullable(params())
                .map(FileOrUUID::binaryfiledataset)
                .map(BinaryFileDataset::getRequiredAuthorizations);
        if (requiredAuthorizationForFile.isPresent()) {
            for (final Map.Entry<String, List<Ltree>> requiredAuthorizationForFileEntry : requiredAuthorizationForFile.get().entrySet()) {
                final String scope = requiredAuthorizationForFileEntry.getKey();
                final String ltree = requiredAuthorizationForFileEntry.getValue().getFirst().getSql();
                final Set<String> toCompareLtree = requiredAuthorizationInDataBase.getOrDefault(scope, Set.of());
                return toCompareLtree.stream()
                        .anyMatch(ltreeAuth -> ltree.equals(ltreeAuth) ||
                                ltreeAuth.startsWith(ltree + Ltree.SEPARATOR));
            }
        }
        return true;
    }

    public boolean hasRightForPublishOrUnPublish() {
        return canDoOperation(OperationType.publication, dataName(), "noRightForPublish");
    }

    public boolean hasRightForDeposit() {
        return canDoOperation(OperationType.depot, dataName(), SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_DEPOSIT);
    }

    protected boolean hasRightForOperationType(final OperationType operationType, final String dataName) {
        return isApplicationCreator ||
                testPredicateForOperationType(operationType, TRUE_PREDICATE);
    }

    public boolean hasRightForPublishOrUnPublish(final Authorization authorization1) {
        return isApplicationCreator ||
                testPredicateForOperationType(OperationType.publication, TRUE_PREDICATE);
    }

    public boolean isRepository() {
        return isRepository;
    }

    public boolean isApplicationCreator() {
        return isApplicationCreator;
    }
}