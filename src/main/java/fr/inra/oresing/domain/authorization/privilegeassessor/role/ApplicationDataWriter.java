package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.file.FileOrUUID;
import org.apache.commons.collections.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public sealed interface ApplicationDataWriter extends ApplicationUser, DataWriter
        permits ApplicationAdminUser, ApplicationDataDelete, ApplicationDeleteUser, ApplicationDepositWriterUser, ApplicationManagerUser, ApplicationPublishWriterUser {
    boolean canDelete(FileOrUUID fileOrUUID);

    boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID);

    boolean hasRightForDeposit(FileOrUUID fileOrUUID);


    Application application();

    default String applicationName() {
        return application().getName();
    }

    String dataName();

    default boolean isData() {
        return !application().isData(dataName());
    }

    OreSiTechnicalException getException();

    default boolean testRequiredAuthorizations(Map<String, Set<String>> authorizationsScope, Map<String, List<Ltree>> submissionScope) {
        if (submissionScope.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Set<String>> authorizationScopeByReference : authorizationsScope.entrySet()) {
            Set<String> authorizationScopes = authorizationScopeByReference.getValue();
            if (!CollectionUtils.isEmpty(authorizationScopes) && !testSumissionScopeForReference(
                    authorizationScopes,
                    submissionScope.get(authorizationScopeByReference.getKey())
            )) {
                return false;
            }
        }
        return true;
    }

    default boolean testSumissionScopeForReference(Set<String> authorizationScopes, List<Ltree> submissionScopes) {
        if (CollectionUtils.isEmpty(submissionScopes)) {
            return false;
        }
        return submissionScopes.stream()
                .allMatch(sc -> authorizationScopes.contains(sc.getSql()));
    }


    default boolean isDateInRangeAuthorized(
            BinaryFileDataset binaryfiledataset,
            List<AuthorizationParsed> authorizationParseds
    ) {
        final DatePattern<?> datePattern = application().findSubmissionDatePattern(dataName());
        final LocalDateTimeRange submissionIntervalScope = LocalDateTimeRange.of(
                datePattern,
                binaryfiledataset.getFrom(),
                binaryfiledataset.getTo()
        );

        List<LocalDateTimeRange> authorizationMatchingIntervals = new ArrayList<>();

        for (AuthorizationParsed authorizationParsed : authorizationParseds) {
            LocalDateTimeRange authorizationIntervalScope = LocalDateTimeRange.between(
                    Optional.ofNullable(authorizationParsed)
                            .map(AuthorizationParsed::fromDay)
                            .map(LocalDate::atStartOfDay)
                            .orElse(LocalDateTime.MIN),
                    Optional.ofNullable(authorizationParsed)
                            .map(AuthorizationParsed::toDay)
                            .map(LocalDate::atStartOfDay)
                            .orElse(LocalDateTime.MAX)
            );

            if (!submissionIntervalScope.getLowerPointOrMin().isAfter(authorizationIntervalScope.getUpperEndpointOrMax()) &&
                !submissionIntervalScope.getUpperEndpointOrMax().isBefore(authorizationIntervalScope.getLowerPointOrMin())) {

                LocalDateTimeRange intersectionInterval = LocalDateTimeRange.between(
                        !submissionIntervalScope.getLowerPointOrMin().isAfter(authorizationIntervalScope.getLowerPointOrMin()) ?
                                authorizationIntervalScope.getLowerPointOrMin() :
                                submissionIntervalScope.getLowerPointOrMin(),
                        !submissionIntervalScope.getUpperEndpointOrMax().isBefore(authorizationIntervalScope.getUpperEndpointOrMax()) ?
                                authorizationIntervalScope.getUpperEndpointOrMax() :
                                submissionIntervalScope.getUpperEndpointOrMax()
                );

                authorizationMatchingIntervals.add(intersectionInterval);
            }
        }
        return verifyCoverageCompleteness(submissionIntervalScope, authorizationMatchingIntervals);
    }

    private boolean verifyCoverageCompleteness(
            LocalDateTimeRange submissionIntervalScope,
            List<LocalDateTimeRange> authorizationMatchingIntervals
    ) {
        List<LocalDateTimeRange> sortedIntervals = authorizationMatchingIntervals.stream()
                .sorted(Comparator.comparing(LocalDateTimeRange::getLowerPointOrMin))
                .toList();

        LocalDateTime currentCoverageEnd = submissionIntervalScope.getLowerPointOrMin();

        for (LocalDateTimeRange interval : sortedIntervals) {
            if (interval.getLowerPointOrMin().isAfter(currentCoverageEnd)) {
                return false;
            }

            currentCoverageEnd = interval.getUpperEndpointOrMax().isAfter(currentCoverageEnd)
                    ? interval.getUpperEndpointOrMax()
                    : currentCoverageEnd;
        }

        boolean isFullyCovered = !currentCoverageEnd.isBefore(submissionIntervalScope.getUpperEndpointOrMax());

        if (!isFullyCovered) {
            throw getException();
        }

        return true;
    }
    default boolean testAuthorizationParsed(List<AuthorizationParsed> authorizations, FileOrUUID fileOrUUID) {
        List<AuthorizationParsed> authorizationParseds = authorizations.stream()
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

}