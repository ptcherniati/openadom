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
import java.util.stream.Collectors;

public sealed interface ApplicationDataWriter extends ApplicationUser
        permits ApplicationAdminUser, ApplicationDataDelete, ApplicationDeleteUser, ApplicationDepositWriterUser, ApplicationManagerUser, ApplicationPublishWriterUser {
    boolean canDelete(FileOrUUID fileOrUUID);

    boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID);

    boolean hasRightForDeposit(FileOrUUID fileOrUUID);


    Application application();

    default String applicationName() {
        return application().getName();
    }

    ;

    String dataName();

    default boolean isData() {
        return application().isData(dataName());
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
        if (!submissionScopes.stream()
                .allMatch(sc -> authorizationScopes.contains(sc.getSql()))) {
            return false;
        }
        return true;
    }default boolean isDateInRangeAuthorized(
            BinaryFileDataset binaryfiledataset,
            List<AuthorizationParsed> authorizationParseds
    ) {
        // Extraction des dates de soumission
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

        // Liste des intervalles d'intersection
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

            // Vérification du chevauchement
            if (submissionIntervalScope.getRange().lowerEndpoint()
                        .compareTo(authorizationIntervalScope.getRange().upperEndpoint()) <= 0 &&
                submissionIntervalScope.getRange().upperEndpoint()
                        .compareTo(authorizationIntervalScope.getRange().lowerEndpoint()) >= 0) {

                // Calcul de l'intervalle d'intersection
                LocalDateTimeRange intersectionInterval = LocalDateTimeRange.between(
                        submissionIntervalScope.getRange().lowerEndpoint().compareTo(authorizationIntervalScope.getRange().lowerEndpoint()) <= 0 ?
                                authorizationIntervalScope.getRange().lowerEndpoint() :
                                submissionIntervalScope.getRange().lowerEndpoint(),
                        submissionIntervalScope.getRange().upperEndpoint().compareTo(authorizationIntervalScope.getRange().upperEndpoint()) >= 0 ?
                                authorizationIntervalScope.getRange().upperEndpoint() :
                                submissionIntervalScope.getRange().upperEndpoint()
                );

                authorizationMatchingIntervals.add(intersectionInterval);
            }
        }

        // Vérification de la couverture totale
        return verifyCoverageCompleteness(submissionIntervalScope, authorizationMatchingIntervals);
    }

    private boolean verifyCoverageCompleteness(
            LocalDateTimeRange submissionIntervalScope,
            List<LocalDateTimeRange> authorizationMatchingIntervals
    ) {
        // Trier les intervalles par date de début
        List<LocalDateTimeRange> sortedIntervals = authorizationMatchingIntervals.stream()
                .sorted(Comparator.comparing(interval -> interval.getRange().lowerEndpoint()))
                .collect(Collectors.toList());

        LocalDateTime currentCoverageEnd = submissionIntervalScope.getRange().lowerEndpoint();

        for (LocalDateTimeRange interval : sortedIntervals) {
            // Vérifier si l'intervalle couvre le trou précédent
            if (interval.getRange().lowerEndpoint().compareTo(currentCoverageEnd) > 0) {
                return false;  // Trou dans la couverture
            }

            // Mettre à jour la fin de couverture
            currentCoverageEnd = interval.getRange().upperEndpoint().compareTo(currentCoverageEnd) > 0
                    ? interval.getRange().upperEndpoint()
                    : currentCoverageEnd;
        }

        // Vérifier si la couverture atteint la fin de l'intervalle de soumission
        boolean isFullyCovered = currentCoverageEnd.compareTo(submissionIntervalScope.getRange().upperEndpoint()) >= 0;

        if (!isFullyCovered) {
            throw getException();
        }

        return true;
    }

}
