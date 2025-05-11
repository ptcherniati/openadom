package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.apache.commons.collections.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

public sealed interface ApplicationDataWriter extends ApplicationUser
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
        LocalDateTime from = Optional.ofNullable(binaryfiledataset)
                .map(BinaryFileDataset::getFrom)
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .map(date -> LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .orElse(LocalDateTime.MIN);

        LocalDateTime to = Optional.ofNullable(binaryfiledataset)
                .map(BinaryFileDataset::getTo)
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .map(date -> LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .orElse(LocalDateTime.MAX);

        LocalDateTimeRange submissionIntervalScope = LocalDateTimeRange.between(from, to);

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

            if (!submissionIntervalScope.getRange().lowerEndpoint().isAfter(authorizationIntervalScope.getRange().upperEndpoint()) &&
                    !submissionIntervalScope.getRange().upperEndpoint().isBefore(authorizationIntervalScope.getRange().lowerEndpoint())) {

                LocalDateTimeRange intersectionInterval = LocalDateTimeRange.between(
                        !submissionIntervalScope.getRange().lowerEndpoint().isAfter(authorizationIntervalScope.getRange().lowerEndpoint()) ?
                                authorizationIntervalScope.getRange().lowerEndpoint() :
                                submissionIntervalScope.getRange().lowerEndpoint(),
                        !submissionIntervalScope.getRange().upperEndpoint().isBefore(authorizationIntervalScope.getRange().upperEndpoint()) ?
                                authorizationIntervalScope.getRange().upperEndpoint() :
                                submissionIntervalScope.getRange().upperEndpoint()
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
                .sorted(Comparator.comparing(interval -> interval.getRange().lowerEndpoint()))
                .toList();

        LocalDateTime currentCoverageEnd = submissionIntervalScope.getRange().lowerEndpoint();

        for (LocalDateTimeRange interval : sortedIntervals) {
            if (interval.getRange().lowerEndpoint().isAfter(currentCoverageEnd)) {
                return false;
            }

            currentCoverageEnd = interval.getRange().upperEndpoint().isAfter(currentCoverageEnd)
                    ? interval.getRange().upperEndpoint()
                    : currentCoverageEnd;
        }

        boolean isFullyCovered = !currentCoverageEnd.isBefore(submissionIntervalScope.getRange().upperEndpoint());

        if (!isFullyCovered) {
            throw getException();
        }

        return true;
    }

}